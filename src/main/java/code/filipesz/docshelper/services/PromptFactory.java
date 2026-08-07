package code.filipesz.docshelper.services;

import org.springframework.stereotype.Component;

@Component
public class PromptFactory {

    public String createSystemPrompt(String action, String lowerQuery, String context) {

        // Instrukcja dla wygenerowania quizu w czystym jsonie, żeby javascript wygenerował quiz w GUI
        if (isQuizRequest(action, lowerQuery)) {
            return """
                    Jesteś modułem tworzącym interaktywne quizy na podstawie podanego KONTEKSTU.
                    
                    Rygorystyczne zasady:
                    1. Wygeneruj dokładnie 5 pytań jednokrotnego wyboru.
                    2. Cała treść pytań oraz opcje odpowiedzi MUSZĄ być w JĘZYKU POLSKIM.
                    3. ZWRÓĆ WYŁĄCZNIE CZYSTY JSON (tablicę obiektów) bez żadnego dodatkowego tekstu i bez znaczników ```json.
                    4. Odpowiedź MUSI dokładnie odpowiadać tej strukturze JSON:
                    [
                      \\{
                        "question": "Treść pytania po polsku",
                        "options": ["Opcja A", "Opcja B", "Opcja C", "Opcja D"],
                        "correctIndex": 0
                      \\}
                    ]
                    
                    KONTEKST DO QUIZU:
                    %s
                    """.formatted(context);
        }

        // Instrukcja dla podsumowania
        if (isSummaryRequest(action, lowerQuery)) {
            return """
                    Jesteś rygorystycznym analitykiem dokumentów DocsHelper.
                    
                    BEZWZGLĘDNE ZASADY STRESZCZANIA:
                    1. Odpowiadaj BEZWZGLĘDNIE i wyłącznie w JĘZYKU POLSKIM.
                    2. Przygotuj zwięzłe, punktowe streszczenie najistotniejszych faktów na podstawie dostarczonego KONTEKSTU.
                    3. BEZWZGLĘDNIE POMIŃ jakiekolwiek wstępy, powitania i komentarze meta.
                    4. Odpowiadaj bezpośrednio konkretami wyciągniętymi z tekstu w formie listy wypunktowanej.
                    5. Jeśli KONTEKST nie zawiera wystarczających danych, napisz krótko: "Brak wystarczających danych w dokumencie."
                    
                    KONTEKST DOKUMENTÓW:
                    %s
                    """.formatted(context);
        }

        // Dla dla randomowej wiadomości
        return """
                Jesteś precyzyjnym asystentem wiedzy DocsHelper.
                
                BEZWZGLĘDNE ZASADY:
                1. Odpowiadaj BEZWZGLĘDNIE w JĘZYKU POLSKIM.
                2. Odpowiadaj WYŁĄCZNIE na podstawie dostarczonego KONTEKSTU DOKUMENTÓW.
                3. Jeśli w KONTEKŚCIE nie ma bezpośredniej odpowiedzi na pytanie, odpowiedz wprost: "Nie znalazłem takich informacji w udostępnionych dokumentach."
                4. NIE UŻYWAJ swojej wiedzy ogólnej do dopowiadań ani zgadywania faktów.
                5. Cytuj liczby, nazwy i konkretne dane dokładnie tak, jak występują w tekście.
                
                KONTEKST DOKUMENTÓW:
                %s
                """.formatted(context.isEmpty() ? "Brak relewantnego kontekstu." : context);
    }

    public boolean isQuizRequest(String action, String lowerQuery) {
        return "quiz".equalsIgnoreCase(action)
                || lowerQuery.contains("quiz")
                || lowerQuery.contains("test")
                || lowerQuery.contains("pytania sprawdzające");
    }

    public boolean isSummaryRequest(String action, String lowerQuery) {
        return "summarize".equalsIgnoreCase(action)
                || lowerQuery.contains("streść")
                || lowerQuery.contains("streszczenie")
                || lowerQuery.contains("podsumuj")
                || lowerQuery.contains("podsumowanie")
                || lowerQuery.contains("skróć");
    }
}