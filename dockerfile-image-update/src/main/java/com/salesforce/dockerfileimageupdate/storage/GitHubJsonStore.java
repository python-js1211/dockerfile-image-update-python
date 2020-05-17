package com.salesforce.dockerfileimageupdate.storage;

import com.google.gson.*;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.GitHubUtil;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;

public class GitHubJsonStore {
    private static final Logger log = LoggerFactory.getLogger(GitHubJsonStore.class);
    private final GitHubUtil gitHubUtil;
    private final String store;

    public GitHubJsonStore(GitHubUtil gitHubUtil, String store) {
        this.gitHubUtil = gitHubUtil;
        this.store = store;
    }

    /* The store link should be a repository name on Github. */
    public void updateStore(String img, String tag) throws IOException {
        if (store == null) {
            log.info("Image tag store cannot be null. Skipping store update...");
            return;
        }
        log.info("Updating store: {} with image: {} tag: {}...", store, img, tag);
        GHRepository storeRepo;
        try {
            GHMyself myself = gitHubUtil.getMyself();
            String ownerOrg = myself.getLogin();
            storeRepo = gitHubUtil.getRepo(Paths.get(ownerOrg, store).toString());
        } catch (IOException e) {
            storeRepo = gitHubUtil.createPublicRepo(store);
        }
        updateStoreOnGithub(storeRepo, Constants.STORE_JSON_FILE, img, tag);
    }

    protected void updateStoreOnGithub(GHRepository repo, String path, String img, String tag) throws IOException {
        try {
            repo.getFileContent(path);
        } catch (IOException e) {
            repo.createContent().content("").message("initializing store").path(path).commit();
        }

        String latestCommit = repo.getBranches().get(repo.getDefaultBranch()).getSHA1();
        log.info("Loading image store at commit {}", latestCommit);
        GHContent content = repo.getFileContent(path, latestCommit);

        if (content.isFile()) {
            JsonElement json;
            try (InputStream stream = content.read(); InputStreamReader streamR = new InputStreamReader(stream)) {
                try {
                    json = new JsonParser().parse(streamR);
                } catch (JsonParseException e) {
                    log.warn("Not a JSON format store. Clearing and rewriting as JSON...");
                    json = JsonNull.INSTANCE;
                }
            }
            String jsonOutput = getAndModifyJsonString(json, img, tag);
            content.update(jsonOutput,
                    String.format("Updated image %s with tag %s.\n@rev none@", img, tag), repo.getDefaultBranch());
        }
    }

    protected String getAndModifyJsonString(JsonElement json, String img, String tag) {
        JsonElement images;
        if (json.isJsonNull()) {
            json = new JsonObject();
            images = new JsonObject();
            json.getAsJsonObject().add("images", images);
        }
        images = json.getAsJsonObject().get("images");
        if (images == null) {
            images = new JsonObject();
            json.getAsJsonObject().add("images", images);
            images = json.getAsJsonObject().get("images");
        }
        JsonElement newTag = new JsonPrimitive(tag);
        images.getAsJsonObject().add(img, newTag);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(json);
    }
}
