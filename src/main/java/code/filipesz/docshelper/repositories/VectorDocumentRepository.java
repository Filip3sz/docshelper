package code.filipesz.docshelper.repositories;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VectorDocumentRepository extends JpaRepository<VectorDocumentRepository.DummyVectorEntity, String> {

    // Natywne zapytanie SQL szukające słów kluczowych w tabeli vector_store Spring AI
    @Query(value = """
            SELECT id, content, metadata FROM vector_store 
            WHERE LOWER(content) LIKE LOWER(CONCAT('%%', :keyword, '%%'))
            LIMIT 5
            """, nativeQuery = true)
    List<Object[]> findByKeyword(@Param("keyword") String keyword);

    // Przelotowa encja potrzebna tylko dla Spring Data JPA do obsługi zapytania
    @Entity
    class DummyVectorEntity {
        @Id
        private String id;
    }
}
