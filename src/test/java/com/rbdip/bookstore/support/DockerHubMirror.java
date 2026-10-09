package com.rbdip.bookstore.support;

import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;

public class DockerHubMirror extends ImageNameSubstitutor {

    @Override
    public DockerImageName apply(DockerImageName image) {
        if (!image.getRegistry().isEmpty()) {
            return image;
        }
        String repository = image.getRepository();
        if (!repository.contains("/")) {
            repository = "library/" + repository;
        }
        return image.withRegistry("mirror.gcr.io").withRepository(repository)
                .asCompatibleSubstituteFor(image);
    }

    @Override
    protected String getDescription() {
        return "mirror.gcr.io";
    }
}
