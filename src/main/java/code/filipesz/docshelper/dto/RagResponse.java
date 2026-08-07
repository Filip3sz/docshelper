package code.filipesz.docshelper.dto;

import java.util.List;

// Posiada odpowiedź i źródło, na podstawie którego sformułował odpowiedź
public class RagResponse {

    private String answer;
    private List<SourceDocument> sources;

    public RagResponse() {}

    public RagResponse(String answer, List<SourceDocument> sources) {
        this.answer = answer;
        this.sources = sources;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<SourceDocument> getSources() {
        return sources;
    }

    public void setSources(List<SourceDocument> sources) {
        this.sources = sources;
    }

    public static class SourceDocument {
        private String fileName;
        private String snippet;

        public SourceDocument() {
        }

        public SourceDocument(String fileName, String snippet) {
            this.fileName = fileName;
            this.snippet = snippet;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }
    }
}