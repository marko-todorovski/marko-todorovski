package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.exception.RepositoryValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads public GitHub repositories as ZIP archives for read-only analysis.
 *
 * <p>Only ever contacts the fixed, hard-coded hosts {@code api.github.com} and
 * {@code codeload.github.com}, using an owner/repo/branch extracted from a strict
 * regex on the user-supplied URL. This avoids SSRF: no attacker-controlled host is
 * ever dereferenced.</p>
 */
@Service
public class GitHubDownloadService {

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
            "^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?(?:tree/([A-Za-z0-9_./-]+))?$");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final long maxArchiveBytes;

    public GitHubDownloadService(
            ObjectMapper objectMapper,
            @Value("${app.repository.max-archive-bytes:262144000}") long maxArchiveBytes) {
        this.objectMapper = objectMapper;
        this.maxArchiveBytes = maxArchiveBytes;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public record RepoRef(String owner, String repo, String branchOverride) {
    }

    public record DownloadResult(InputStream zipStream, String owner, String repo, String branch, String commitHash) {
    }

    public RepoRef parseRepoUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new RepositoryValidationException("GitHub URL is required");
        }
        Matcher matcher = GITHUB_URL_PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            throw new RepositoryValidationException(
                    "Only public GitHub repository URLs like https://github.com/{owner}/{repo} are supported");
        }
        return new RepoRef(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    public DownloadResult download(String url) {
        RepoRef ref = parseRepoUrl(url);
        String branch = ref.branchOverride() != null ? ref.branchOverride() : resolveDefaultBranch(ref.owner(), ref.repo());
        String commitHash = resolveCommitHash(ref.owner(), ref.repo(), branch);
        InputStream zipStream = downloadZip(ref.owner(), ref.repo(), branch);
        return new DownloadResult(zipStream, ref.owner(), ref.repo(), branch, commitHash);
    }

    private String resolveDefaultBranch(String owner, String repo) {
        URI uri = URI.create("https://api.github.com/repos/" + owner + "/" + repo);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ai-diagram-generator")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RepositoryValidationException(
                        "GitHub repository could not be found or is not public: " + owner + "/" + repo);
            }
            JsonNode node = objectMapper.readTree(response.body());
            JsonNode defaultBranch = node.get("default_branch");
            if (defaultBranch == null || defaultBranch.asText().isBlank()) {
                throw new RepositoryValidationException("Could not determine the default branch for " + owner + "/" + repo);
            }
            return defaultBranch.asText();
        } catch (IOException | InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
            throw new RepositoryValidationException("Could not reach GitHub to resolve the repository: " + e.getMessage());
        }
    }

    private String resolveCommitHash(String owner, String repo, String branch) {
        URI uri = URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/commits/" + branch);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ai-diagram-generator")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode node = objectMapper.readTree(response.body());
            JsonNode sha = node.get("sha");
            return sha == null ? null : sha.asText();
        } catch (IOException | InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private InputStream downloadZip(String owner, String repo, String branch) {
        URI uri = URI.create("https://codeload.github.com/" + owner + "/" + repo + "/zip/refs/heads/" + branch);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("User-Agent", "ai-diagram-generator")
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new RepositoryValidationException(
                        "Could not download archive for " + owner + "/" + repo + "@" + branch);
            }
            return new SizeLimitedInputStream(response.body(), maxArchiveBytes);
        } catch (IOException | InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
            throw new RepositoryValidationException("Could not download the repository archive: " + e.getMessage());
        }
    }

    /**
     * Aborts the download once more than {@code maxBytes} have been read, protecting against
     * oversized or unbounded archives before they are ever passed to the ZIP scanner.
     */
    private static final class SizeLimitedInputStream extends FilterInputStream {
        private final long maxBytes;
        private long readSoFar;

        SizeLimitedInputStream(InputStream in, long maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                readSoFar++;
                checkLimit();
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                readSoFar += n;
                checkLimit();
            }
            return n;
        }

        private void checkLimit() throws IOException {
            if (readSoFar > maxBytes) {
                throw new IOException("Repository archive exceeds the maximum allowed size of " + maxBytes + " bytes");
            }
        }
    }
}
