<h1>DOCSHELPER - RAG ASSISTANT</h1>
<p>
  <b>DocsHelper</b> is a lightweight, privacy-focused Retrieval-Augmented Generation (RAG) application built with Spring Boot 3.3.0 and Spring AI (1.0.0-M1). It allows users to maintain contextual chat sessions backed by their uploaded documents and multi-modal image files. The system runs completely locally using Ollama, indexing text and visual contents into a persistent PostgreSQL (pgvector) store to deliver accurate, localized responses without external API dependencies.
</p>

<h2>System Capabilities</h2>
<p>
  <b>Multi-Format Ingestion & Visual OCR:</b> Native support for text documents (PDF, DOCX, TXT, RTF, MD, CSV) via Apache Tika and automated OCR/visual description for images (PNG, JPG, WEBP) with proportional downscaling via <code>qwen3.5:9b</code>.<br>
  <b>Persistent Vector Indexing (PgVector):</b> Splits documents with <code>TokenTextSplitter</code> and stores <code>bge-m3</code> embeddings inside PostgreSQL with the <code>pgvector</code> extension.<br>
  <b>Scoped Context Retrieval:</b> Dynamic per-chat file selection restricts semantic vector search strictly to checked documents.<br>
  <b>Real-Time SSE Streaming:</b> Reactive <code>Flux&lt;String&gt;</code> streaming with instant client-side cancellation and concurrency locks for active files and chats.<br>
  <b>Educational Assistant & Quiz Engine:</b> Grounded Polish Q&A, guided explanations, and interactive A–D quiz verification based strictly on uploaded material.<br>
  <b>LearnAuditor Exam Bridge:</b> One-click generation of structured open-ended exams (title, study intro, tasks, 100-point grading criteria, reference answers) sent directly as drafts to LearnAuditor.
</p>

<h2>Tech Stack</h2>
<p>
  <b>Language & Framework:</b> Java 21, Spring Boot 3.3.0 (Spring MVC, Spring WebFlux, Spring Data JPA)<br>
  <b>AI Framework:</b> Spring AI 1.0.0-M1 (<code>ChatClient</code>, <code>PgVectorStore</code>, <code>TikaDocumentReader</code>, <code>TokenTextSplitter</code>)<br>
  <b>LLM Runtime & Active Models:</b> Local Ollama — Chat, Exam Generation & Vision/OCR: <code>qwen3.5:9b</code> | Embeddings: <code>bge-m3</code><br>
  <b>Content & Image Processing:</b> Apache Tika and Java 2D / <code>ImageIO</code> bicubic scaling<br>
  <b>Database & Vector Store:</b> PostgreSQL 16 with <code>pgvector</code> (HNSW, cosine distance)<br>
  <b>Security & Integration:</b> Spring Security (CSRF), <code>RestClient</code> bridge to LearnAuditor, Jackson JSON<br>
  <b>Web Interface & Build:</b> Server-rendered Thymeleaf, Aurora Interface CSS, EventSource SSE JS, Maven, Lombok, Docker Compose
</p>

<h2>Architecture & Technical Solutions</h2>
<p>
  <b>1. Domain-Separated Services & Multi-Modal Ingestion</b><br>
  Responsibilities are split across <code>KnowledgeBaseService</code>, <code>ChatService</code>, <code>MessageService</code>, and <code>DashboardService</code>. Text files are parsed with <code>TikaDocumentReader</code>, chunked via <code>TokenTextSplitter</code>, and tagged with <code>file_name</code> metadata. Images exceeding 1600 px are proportionally scaled by <code>ImageScalerService</code> before visual transcription by <code>qwen3.5:9b</code> and vector indexing with <code>bge-m3</code>.
</p>
<p>
  <b>2. Filtered Vector Retrieval & Ollama Thinking Control</b><br>
  <code>KnowledgeBaseService</code> builds dynamic <code>FilterExpressionBuilder</code> predicates so similarity queries only scan files selected for the current chat. <code>OllamaConfig</code> customizes both <code>RestClient</code> and reactive <code>WebClient</code> calls to disable the default reasoning mode (<code>think: false</code>) in <code>qwen3.5:9b</code> for faster chat and OCR responses.
</p>
<p>
  <b>3. Reactive SSE Streaming & Concurrency Guards</b><br>
  <code>MessageController</code> streams chunks over Server-Sent Events while <code>MessageService</code> aggregates the full reply and saves user/AI messages transactionally on completion. In-memory operation locks in <code>ChatService</code> and <code>KnowledgeBaseService</code> prevent deleting or modifying files and chats currently used by an active stream.
</p>
<p>
  <b>4. LearnAuditor Exam Export Bridge</b><br>
  <code>DocumentAssistantService</code> uses a dedicated JSON-mode prompt to generate and validate open-ended tasks with 100-point grading rubrics from selected notes. <code>LearnauditorService</code> posts the validated <code>ExamDraftRequest</code> to LearnAuditor and returns a one-time import link to the chat UI.
</p>

<h2>Installation & Configuration</h2>
<p>
  <b>1. Prerequisites</b><br>
  Ensure you have <b>JDK 21</b>, <b>Maven</b>, <b>Ollama</b>, and <b>Docker Desktop</b> installed on your environment.
</p>
<p>
  <b>2. Setup Local Models via Ollama</b><br>
  Pull the required LLM chat/vision, and embedding models in your terminal:
</p>
<pre><code>ollama pull qwen3.5:9b
ollama pull bge-m3</code></pre>
<p>
  <b>3. Start PostgreSQL with pgvector via Docker</b><br>
  Before run the PostgreSQL container with the <code>pgvector</code> extension enabled set <code>POSTGRES_USER</code> and <code>POSTGRES_PASSWORD</code> in your shell or local <code>.env</code> file before starting Docker.
  For an existing database volume, use the credentials already configured in PostgreSQL.
</p>
<pre><code>docker compose up -d</code></pre>
<p>
  <b>4. Run the Application</b><br>
  Provide <code>POSTGRES_USER</code> and <code>POSTGRES_PASSWORD</code> in the local <code>.env</code> file or as environment variables for Spring Boot. Then Build and start the Spring Boot backend using Maven:
</p>
<pre><code>mvn clean spring-boot:run</code></pre>
<p>
  Once started, navigate to <code>http://localhost:8080</code> in your browser to access the DocsHelper dashboard.
</p>

<hr>

<h1>DOCSHELPER - ASYSTENT RAG</h1>
<p>
  <b>DocsHelper</b> to lekki, nastawiony na prywatność asystent typu RAG (Retrieval-Augmented Generation) zbudowany w oparciu o Spring Boot 3.3.0 oraz Spring AI (1.0.0-M1). Umożliwia prowadzenie interaktywnych rozmów z własną bazą wiedzy złożoną z dokumentów tekstowych oraz plików graficznych. Całość działa w 100% lokalnie dzięki środowisku Ollama oraz bazie PostgreSQL (pgvector), zapewniając pełne bezpieczeństwo danych bez używania zewnętrznych API.
</p>

<h2>Możliwości Systemu</h2>
<p>
  <b>Multimodalne Przetwarzanie i OCR Obrazów:</b> Obsługa dokumentów tekstowych (PDF, DOCX, TXT, RTF, MD, CSV) przez Apache Tika oraz opis i OCR obrazów (PNG, JPG, WEBP) z proporcjonalnym skalowaniem w <code>qwen3.5:9b</code>.<br>
  <b>Trwałe Indeksowanie Wektorowe (PgVector):</b> Dzielenie tekstu przez <code>TokenTextSplitter</code> i zapis embeddingów <code>bge-m3</code> w PostgreSQL z rozszerzeniem <code>pgvector</code>.<br>
  <b>Precyzyjne Filtrowanie Kontekstu:</b> Wybór aktywnych plików dla każdej rozmowy ogranicza wyszukiwanie semantyczne wyłącznie do zaznaczonych dokumentów.<br>
  <b>Strumieniowanie Odpowiedzi w Czasie Rzeczywistym (SSE):</b> Przesyłanie odpowiedzi przez <code>Flux&lt;String&gt;</code> z natychmiastowym anulowaniem oraz blokadą edycji używanych plików i rozmów.<br>
  <b>Tryb Edukacyjny i Generator Quizów:</b> Odpowiedzi po polsku ściśle oparte na źródłach oraz interaktywne pytania jednokrotnego wyboru A–D.<br>
  <b>Most Eksportu do LearnAuditora:</b> Generowanie jednym przyciskiem pełnych egzaminów otwartych (tytuł, wprowadzenie, zadania, kryteria na 100 pkt i odpowiedzi wzorcowe) przesyłanych jako szkic do platformy LearnAuditor.
</p>

<h2>Stos Technologiczny</h2>
<p>
  <b>Język i Framework:</b> Java 21, Spring Boot 3.3.0 (Spring MVC, Spring WebFlux, Spring Data JPA)<br>
  <b>Architektura AI:</b> Spring AI 1.0.0-M1 (<code>ChatClient</code>, <code>PgVectorStore</code>, <code>TikaDocumentReader</code>, <code>TokenTextSplitter</code>)<br>
  <b>Środowisko LLM i Modele:</b> Lokalne Ollama — Czat, Generator Egzaminów i Wizja/OCR: <code>qwen3.5:9b</code> | Embeddingi: <code>bge-m3</code><br>
  <b>Przetwarzanie Treści i Obrazów:</b> Apache Tika oraz skalowanie bikubiczne Java 2D / <code>ImageIO</code><br>
  <b>Baza Danych i Magazyn Wektorowy:</b> PostgreSQL 16 z rozszerzeniem <code>pgvector</code> (HNSW, odległość cosinusowa)<br>
  <b>Bezpieczeństwo i Integracja:</b> Spring Security (CSRF), most HTTP <code>RestClient</code> do LearnAuditora, Jackson JSON<br>
  <b>Interfejs Webowy i Budowanie:</b> Thymeleaf SSR, motyw Aurora Interface CSS, EventSource SSE w JS, Maven, Lombok i Docker Compose
</p>

<h2>Architektura i Rozwiązania Techniczne</h2>
<p>
  <b>1. Podział Domenowy Serwisów i Multimodalny Pipeline Plików</b><br>
  Logika jest rozdzielona pomiędzy <code>KnowledgeBaseService</code>, <code>ChatService</code>, <code>MessageService</code> i <code>DashboardService</code>. Dokumenty tekstowe są dzielone przez <code>TikaDocumentReader</code> i <code>TokenTextSplitter</code> z metadanymi <code>file_name</code>. Obrazy powyżej 1600 px są proporcjonalnie zmniejszane w <code>ImageScalerService</code>, opisywane przez <code>qwen3.5:9b</code> i indeksowane wektorowo modelem <code>bge-m3</code>.
</p>
<p>
  <b>2. Kontekstowe Wyszukiwanie Wektorowe i Konfiguracja Ollamy</b><br>
  <code>KnowledgeBaseService</code> buduje filtry <code>FilterExpressionBuilder</code> ograniczające zapytania wektorowe do plików przypisanych do rozmowy. <code>OllamaConfig</code> dostosowuje <code>RestClient</code> oraz reaktywny <code>WebClient</code>, wyłączając domyślny tryb myślenia (<code>think: false</code>) w modelu <code>qwen3.5:9b</code> dla szybszego czatu i OCR.
</p>
<p>
  <b>3. Strumieniowanie SSE i Kontrola Współbieżności</b><br>
  <code>MessageController</code> przesyła fragmenty odpowiedzi przez Server-Sent Events, a <code>MessageService</code> składa pełną treść i po zakończeniu zapisuje parę wiadomości w PostgreSQL. Blokady aktywnych operacji w <code>ChatService</code> i <code>KnowledgeBaseService</code> chronią używane pliki i rozmowy przed równoczesnym usunięciem.
</p>
<p>
  <b>4. Most Generowania Egzaminów do LearnAuditora</b><br>
  <code>DocumentAssistantService</code> korzysta z osobnego promptu w trybie JSON do generowania i walidacji zadań otwartych z punktacją na 100 pkt na bazie notatek. <code>LearnauditorService</code> przesyła zwalidowany <code>ExamDraftRequest</code> do LearnAuditora i zwraca do czatu jednorazowy link importu.
</p>

<h2>Instalacja i Konfiguracja</h2>
<p>
  <b>1. Wymagania Wstępne</b><br>
  Upewnij się, że w Twoim środowisku zainstalowane są: <b>JDK 21</b>, <b>Maven</b>, silnik <b>Ollama</b> oraz <b>Docker Desktop</b>.
</p>
<p>
  <b>2. Pobranie Modeli Lokalnych w Ollama</b><br>
  Pobierz wymagane modele dla czatu/wizji oraz embeddingów:
</p>
<pre><code>ollama pull qwen3.5:9b
ollama pull bge-m3</code></pre>
<p>
  <b>3. Uruchomienie PostgreSQL z rozszerzeniem pgvector w Dockerze</b><br>
  Zanim uruchomisz kontener PostgreSQL z aktywną obsługą wektorów ustaw <code>POSTGRES_USER</code> oraz <code>POSTGRES_PASSWORD</code> w terminalu lub lokalnym pliku <code>.env</code>.
  Przy istniejącym wolumenie użyj danych konta skonfigurowanego już w PostgreSQL.
</p>
<pre><code>docker compose up -d</code></pre>
<p>
  <b>4. Uruchomienie Aplikacji</b><br>
  Ustaw <code>POSTGRES_USER</code> i <code>POSTGRES_PASSWORD</code> w lokalnym pliku <code>.env</code> lub w środowisku aplikacji. Następnie skompiluj i uruchom aplikację Spring Boot przy użyciu Mavena:
</p>
<pre><code>mvn clean spring-boot:run</code></pre>
<p>
  Po pomyślnym uruchomieniu przejdź pod adres <code>http://localhost:8080</code> w przeglądarce, aby otworzyć panel aplikacji DocsHelper.</p>