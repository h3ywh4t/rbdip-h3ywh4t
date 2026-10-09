package com.rbdip.bookstore.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.utility.DockerImageName;

class DockerHubMirrorTest {

    private final DockerHubMirror mirror = new DockerHubMirror();

    @ParameterizedTest
    @CsvSource({
        "postgres:16-alpine, mirror.gcr.io/library/postgres:16-alpine",
        "library/postgres:16-alpine, mirror.gcr.io/library/postgres:16-alpine",
        "testcontainers/ryuk:0.8.1, mirror.gcr.io/testcontainers/ryuk:0.8.1",
        "alpine:3.17, mirror.gcr.io/library/alpine:3.17",
        "postgres@sha256:abc123, mirror.gcr.io/library/postgres@sha256:abc123"
    })
    void mapsDockerHubImagesWithoutChangingVersions(String source, String expected) {
        DockerImageName original = DockerImageName.parse(source);

        DockerImageName substituted = mirror.apply(original);

        assertThat(substituted.asCanonicalNameString()).isEqualTo(expected);
        assertThat(substituted.isCompatibleWith(original)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ghcr.io/example/postgres:16", "localhost:5000/postgres:16"})
    void preservesExplicitRegistries(String source) {
        DockerImageName original = DockerImageName.parse(source);

        assertThat(mirror.apply(original)).isSameAs(original);
    }
}
