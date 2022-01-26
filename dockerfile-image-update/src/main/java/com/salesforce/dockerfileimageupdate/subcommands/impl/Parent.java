/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.SubCommand;
import com.salesforce.dockerfileimageupdate.model.GitForkBranch;
import com.salesforce.dockerfileimageupdate.model.GitHubContentToProcess;
import com.salesforce.dockerfileimageupdate.model.PullRequestInfo;
import com.salesforce.dockerfileimageupdate.process.ForkableRepoValidator;
import com.salesforce.dockerfileimageupdate.process.GitHubPullRequestSender;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import com.salesforce.dockerfileimageupdate.utils.ResultsProcessor;
import com.salesforce.dockerfileimageupdate.subcommands.commonsteps.Common;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedSearchIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SubCommand(help="updates all repositories' Dockerfiles with given base image",
        requiredParams = {Constants.IMG, Constants.TAG, Constants.STORE})

public class Parent implements ExecutableWithNamespace {

    private static final Logger log = LoggerFactory.getLogger(Parent.class);

    DockerfileGitHubUtil dockerfileGitHubUtil;

    @Override
    public void execute(final Namespace ns, DockerfileGitHubUtil dockerfileGitHubUtil)
            throws IOException, InterruptedException {
        loadDockerfileGithubUtil(dockerfileGitHubUtil);
        String img = ns.get(Constants.IMG);
        String tag = ns.get(Constants.TAG);

        log.info("Updating store...");
        this.dockerfileGitHubUtil.getGitHubJsonStore(ns.get(Constants.STORE)).updateStore(img, tag);

        if (ns.get(Constants.SKIP_PR_CREATION)) {
            log.info("Since the flag {} is set to True, the PR creation steps will "
                    + "be skipped.", Constants.SKIP_PR_CREATION);
            return;
        }
        Common commonSteps = getCommon();
        GitHubPullRequestSender pullRequestSender = getPullRequestSender(dockerfileGitHubUtil, ns);
        GitForkBranch gitForkBranch = getGitForkBranch(ns);
        log.info("Finding Dockerfiles with the given image...");

        Integer gitApiSearchLimit = ns.get(Constants.GIT_API_SEARCH_LIMIT);
        Optional<List<PagedSearchIterable<GHContent>>> contentsWithImage = dockerfileGitHubUtil.getGHContents(ns.get(Constants.GIT_ORG), img, gitApiSearchLimit);

        if (contentsWithImage.isPresent()) {
            List<PagedSearchIterable<GHContent>> contentsFoundWithImage = contentsWithImage.get();
            for (int i = 0; i < contentsFoundWithImage.size(); i++ ) {
                try {
                    commonSteps.prepareToCreatePullRequests(ns, pullRequestSender,
                            contentsFoundWithImage.get(i), gitForkBranch, dockerfileGitHubUtil);
                } catch (IOException e) {
                    log.error("Could not send pull request.", e);
                }
            }
        }
    }

    protected Common getCommon(){
        return new Common();
    }

    protected GitForkBranch getGitForkBranch(Namespace ns){
        return new GitForkBranch(ns.get(Constants.IMG), ns.get(Constants.TAG), ns.get(Constants.GIT_BRANCH));
    }

    protected GitHubPullRequestSender getPullRequestSender(DockerfileGitHubUtil dockerfileGitHubUtil, Namespace ns){
        return new GitHubPullRequestSender(dockerfileGitHubUtil, new ForkableRepoValidator(dockerfileGitHubUtil),
                ns.get(Constants.GIT_REPO_EXCLUDES));
    }

    protected void loadDockerfileGithubUtil(DockerfileGitHubUtil _dockerfileGitHubUtil) {
        dockerfileGitHubUtil = _dockerfileGitHubUtil;
    }
}
