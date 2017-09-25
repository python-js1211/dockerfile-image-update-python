package com.salesforce.dva.dockerfileimageupdate;


import com.google.common.reflect.ClassPath;
import com.salesforce.dva.dockerfileimageupdate.githubutils.GithubUtil;
import com.salesforce.dva.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dva.dockerfileimageupdate.utils.DockerfileGithubUtil;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import static com.salesforce.dva.dockerfileimageupdate.utils.Constants.COMMAND;
import static com.salesforce.dva.dockerfileimageupdate.utils.Constants.GIT_API;

/**
 * Created by minho-park on 6/29/2016.
 */
public class CommandLine {
    private final static Logger log = LoggerFactory.getLogger(CommandLine.class);

    /* Should never actually be instantiated in code */
    private CommandLine () { }

    public static void main(String[] args)
            throws IOException, IllegalAccessException, InstantiationException, InterruptedException {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("dockerfile-image-update", true)
                .description("Image Updates through Pull Request Automator");

        parser.addArgument("-o", "--org")
                .help("search within specific organization (default: all of github)");
        /* Currently, because of argument passing reasons, you can only specify one branch. */
        parser.addArgument("-b", "--branch")
                .help("make pull requests for given branch name (default: master)");
        parser.addArgument("-g", "--" + GIT_API)
                .help("link to github api; overrides environment variable");
        parser.addArgument("-f", "--auto-merge").action(Arguments.storeTrue())
                .help("NOT IMPLEMENTED / set to automatically merge pull requests if available");
        parser.addArgument("-m")
                .help("message to provide for pull requests");
        parser.addArgument("-c")
                .help("additional commit message for the commits in pull requests");

        Set<ClassPath.ClassInfo> allClasses = findSubcommands(parser);
        Namespace ns = handleArguments(parser, args);
        if (ns == null)
            System.exit(1);
        Class<?> runClass = loadCommand(allClasses, ns.get(COMMAND));
        DockerfileGithubUtil dockerfileGithubUtil = initializeDockerfileGithubUtil(ns.get(GIT_API));

        /* Execute given command. */
        ((ExecutableWithNamespace)runClass.newInstance()).execute(ns, dockerfileGithubUtil);
    }

    /*  Adding subcommands to the subcommands list.
            argparse4j allows commands to be truncated, so users can type the first letter (a,c,p) for commands */
    public static Set<ClassPath.ClassInfo> findSubcommands(ArgumentParser parser) throws IOException {
        Subparsers subparsers = parser.addSubparsers()
                .dest(COMMAND)
                .help("FEATURE")
                .title("subcommands")
                .description("Specify which feature to perform")
                .metavar("COMMAND");

        Set<ClassPath.ClassInfo> allClasses = new TreeSet<>(new Comparator<ClassPath.ClassInfo>() {
            @Override
            public int compare(ClassPath.ClassInfo l, ClassPath.ClassInfo r) {
                return l.getName().compareTo(r.getName());
            }
        });
        ClassPath classpath = ClassPath.from(CommandLine.class.getClassLoader());
        allClasses.addAll(classpath.getTopLevelClasses("com.salesforce.dva.dockerfileimageupdate.subcommands.impl"));

        for (ClassPath.ClassInfo classInfo : allClasses) {
            handleAnnotations(classInfo, subparsers);
        }
        return allClasses;
    }

    /* Looks at SubCommand annotation to pull information about each command. */
    public static void handleAnnotations(ClassPath.ClassInfo classInfo, Subparsers subparsers) throws IOException {
        Class<?> clazz = classInfo.load();
        if (clazz.isAnnotationPresent(SubCommand.class)) {
            SubCommand subCommand = clazz.getAnnotation(SubCommand.class);
            if (subCommand.ignore()) {
                return;
            }
            Subparser subparser = subparsers.addParser(classInfo.getSimpleName().toLowerCase()).help(subCommand.help());
            for (String requiredArg : subCommand.requiredParams()) {
                if (requiredArg.isEmpty()) {
                    continue;
                }
                subparser.addArgument(requiredArg).required(true).help("REQUIRED");
            }
            boolean tag = true;
            Argument arg = null;
            for (String optionalArg : subCommand.optionalParams()) {
                if (optionalArg.isEmpty()) {
                    continue;
                }
                if (tag) {
                    arg = subparser.addArgument("-" + optionalArg).help("OPTIONAL");
                    tag = false;
                } else {
                    arg.dest(optionalArg);
                    tag = true;
                }
            }

        } else {
            throw new IOException("There is a command without annotation: " + clazz.getSimpleName());
        }
    }

    /* Checks if the args passed into the command line is valid. */
    public static Namespace handleArguments(ArgumentParser parser, String[] args) {
        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
        }
        return ns;
    }

    /* Find command using reflection. */
    public static Class<?> loadCommand(Set<ClassPath.ClassInfo> allClasses, String command) throws IOException {
        Class<?> runClass = null;
        for (ClassPath.ClassInfo classInfo : allClasses) {
            if (classInfo.getSimpleName().equalsIgnoreCase(command)) {
                runClass = classInfo.load();
            }
        }
        if (runClass == null) {
            throw new IOException("FATAL: Could not execute command.");
        }
        return runClass;
    }

    /* Validate API URL and connect to the API using credentials. */
    public static DockerfileGithubUtil initializeDockerfileGithubUtil(String gitApiUrl) throws IOException {
        if (gitApiUrl == null) {
            gitApiUrl = System.getenv("git_api_url");
            if (gitApiUrl == null) {
                throw new IOException("No Git API URL in environment variables.");
            }
        }
        String token = System.getenv("git_api_token");
        if (token == null) {
            log.error("Please provide GitHub token in environment variables.");
            System.exit(3);
        }

        GitHub github = new GitHubBuilder().withEndpoint(gitApiUrl)
                .withOAuthToken(token)
                .build();
        github.checkApiUrlValidity();

        GithubUtil githubUtil = new GithubUtil(github);
        DockerfileGithubUtil dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);

        return dockerfileGithubUtil;
    }
}
