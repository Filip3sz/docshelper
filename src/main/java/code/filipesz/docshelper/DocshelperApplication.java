package code.filipesz.docshelper;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.File;

@SpringBootApplication
public class DocshelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocshelperApplication.class, args);
    }


    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        File databaseFile = new File("vector_database.json");

        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        if (databaseFile.exists()) {
            store.load(databaseFile);
        }

        return store;
    }
}
