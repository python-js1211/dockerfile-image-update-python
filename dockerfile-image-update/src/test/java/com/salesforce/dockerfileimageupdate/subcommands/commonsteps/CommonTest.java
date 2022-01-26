package com.salesforce.dockerfileimageupdate.subcommands.commonsteps;

import com.google.common.collect.*;
import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.process.*;
import com.salesforce.dockerfileimageupdate.utils.*;
import net.sourceforge.argparse4j.inf.*;
import org.kohsuke.github.*;
import org.mockito.*;
import org.testng.annotations.*;

import java.util.*;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class CommonTest {
    @Test
    public void testCommonStepsSuccessful() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.SKIP_PR_CREATION,
                false);
        Namespace ns = new Namespace(nsMap);
        Common commonStep = new Common();
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        PagedSearchIterable<GHContent> contentsFoundWithImage = mock(PagedSearchIterable.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = ArrayListMultimap.create();
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        pathToDockerfilesInParentRepo.put("repo1", gitHubContentToProcess);
        pathToDockerfilesInParentRepo.put("repo2", gitHubContentToProcess);
        when(pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsFoundWithImage, gitForkBranch)).thenReturn(pathToDockerfilesInParentRepo);


        commonStep.prepareToCreatePullRequests(ns, pullRequestSender, contentsFoundWithImage,
                gitForkBranch, dockerfileGitHubUtil);

        Mockito.verify(dockerfileGitHubUtil, times(2)).changeDockerfiles(eq(ns),
                eq(pathToDockerfilesInParentRepo),
                eq(gitHubContentToProcess), anyList(), eq(gitForkBranch));
    }

    @Test
    public void testCommonStepsWhenNoDockerfileFound() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.SKIP_PR_CREATION,
                false);
        Namespace ns = new Namespace(nsMap);
        Common commonStep = new Common();
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        PagedSearchIterable<GHContent> contentsFoundWithImage = mock(PagedSearchIterable.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = mock(Multimap.class);
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        when(pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsFoundWithImage, gitForkBranch)).thenReturn(pathToDockerfilesInParentRepo);
        Set<String> currUsers = new HashSet<>();
        currUsers.add("repo1");
        when(pathToDockerfilesInParentRepo.keySet()).thenReturn(currUsers);
        commonStep.prepareToCreatePullRequests(ns, pullRequestSender, contentsFoundWithImage,
                gitForkBranch, dockerfileGitHubUtil);

        Mockito.verify(dockerfileGitHubUtil, times(0)).changeDockerfiles(eq(ns),
                eq(pathToDockerfilesInParentRepo),
                eq(gitHubContentToProcess), anyList(), eq(gitForkBranch));
    }
}
