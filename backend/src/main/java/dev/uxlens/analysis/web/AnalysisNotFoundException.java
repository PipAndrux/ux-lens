package dev.uxlens.analysis.web;

import java.util.UUID;

class AnalysisNotFoundException extends RuntimeException {

    AnalysisNotFoundException(UUID id) {
        super("No analysis with id " + id + ".");
    }
}
