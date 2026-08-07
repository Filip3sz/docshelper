<h1>DOCSHELPER - RAG ASSISTANT</h1>
<p>
  This application is an intelligent <b>RAG (Retrieval-Augmented Generation)</b> assistant built using the <b>Spring Boot</b> ecosystem and the modern <b>Spring AI</b> framework. It allows users to have contextual conversations with their own documents locally and securely, perform hybrid semantic search across knowledge bases, inspect source citations, and auto-generate interactive quizzes or structured summaries.
</p>

<h2>System Capabilities</h2>
<p>
  <b>Knowledge Ingestion:</b> Users can upload files directly through a modern web interface with drag-and-drop support, automatically parsing formats like PDF, DOCX, RTF, and TXT.<br>
  <b>Semantic Indexing & Smart Chunking:</b> Text content is cleaned, split into optimized chunks with overlap to preserve sentence context, and transformed into vector embeddings stored directly in PostgreSQL via <code>pgvector</code>.<br>
  <b>Intent-Aware Hybrid RAG Engine:</b> Combines semantic vector similarity with traditional SQL keyword search (Hybrid Search). Automatically detects query intent (Quiz, Summary, or Chat) to dynamically adjust retrieval parameters, top-K search limits, similarity thresholds (0.55+ filtering), and system prompts.<br>
  <b>State Persistence & JPA Management:</b> Full chat histories and message relationships are managed through Spring Data JPA and persisted durably inside PostgreSQL database tables.<br>
  <b>Interactive Quiz, Citations & Title Generation:</b> Enforces pure JSON output formats for AI-generated quizzes, returns full DTO payloads with source document citations, streams real-time responses via Server-Sent Events (Flux), and dynamically generates 3-5 word chat titles.<br>
  <b>100% Privacy:</b> Powered by a local LLM engine, meaning no private documents or chat histories ever leave the host machine.
</p>

<h2>Tech Stack</h2>
<p>
  <b>Language:</b> Java 26<br>
  <b>Framework:</b> Spring Boot 4.1.0 (Web MVC, Spring Data JPA, RESTful APIs)<br>
  <b>Database & Vector Store:</b> PostgreSQL with <code>pgvector</code> extension (Docker Container)<br>
  <b>AI Architecture:</b> Spring AI 2.0.0 (Core Builder API, ChatClient, VectorStore, PgVectorStore, Document Transformers)<br>
  <b>LLM Runtime:</b> Ollama (Local macOS Service)<br>
  <b>Active Models:</b> Chat: <code>llama3.1:8b</code> | Embeddings: <code>bge-m3</code><br>
  <b>Content Extraction:</b> Apache Tika (via <code>TikaDocumentReader</code>)<br>
  <b>Build Tool:</b> Maven
</p>

<h2>Architecture & Technical Solutions</h2>
<p>
  <b>1. Semantic Knowledge Storage & Enterprise Persistence (PgVector & JPA)</b><br>
  Registers a <code>PgVectorStore</code> bean integrated with Dockerized PostgreSQL. Entities (<code>Chat</code>, <code>Message</code>) and repositories (<code>ChatRepository</code>) use Spring Data JPA to provide durable relational persistence for full chat history. The vector store handles high-dimensional embeddings natively in PostgreSQL via the <code>pgvector</code> extension, eliminating volatile in-memory limitations while ensuring ACID compliance across all chat and document metadata.
</p>
<p>
  <b>2. Automated Content Parsing & Feature Extraction (Tika Ingestion Engine)</b><br>
  The ingestion layer (<code>RagController</code> / <code>RagService</code>) accepts <code>MultipartFile</code> payloads and uses Apache Tika's <code>TikaDocumentReader</code> to strip formatting and isolate clean text content. Extracted documents are tagged with essential metadata (e.g., <code>file_name</code>) and processed via custom-configured <code>TokenTextSplitter</code> with overlapping windows (800 tokens chunk size, 350 min chars overlap) before being stored atomically using <code>vectorStore.add()</code>. The system also supports metadata cleanup and vector removal (<code>deleteFileFromKnowledgeBase</code>) upon document deletion.
</p>
<p>
  <b>3. Semantic Retrieval & Contextual Prompt Engineering (Hybrid RAG Pipeline)</b><br>
  Realizes advanced retrieval combining vector search with native SQL keyword matching (<code>findByKeyword</code>). The pipeline features intent-based optimization:
  <br>• <b>Chat Mode:</b> Fetches top 5 chunks with a similarity threshold raised to <code>0.55</code> to filter out low-relevance noise.
  <br>• <b>Quiz & Summary Modes:</b> Expands search depth to top 12 chunks (<code>topK(12)</code>) without similarity gating to maximize document coverage.
  <br>• <b>Metadata Filtering:</b> Builds boolean expression trees (e.g., <code>file_name == 'doc.pdf' || ...</code>) to restrict context lookups strictly to user-selected active files.
  <br>• <b>Sliding History Window & Source Citations:</b> Injects up to the 6 most recent message exchanges into the prompt context and encapsulates generated responses alongside exact file snippets inside structured <code>RagResponse</code> DTOs.
  <br>• <b>Structured AI Outputs & Auto-Titling:</b> Forces pure JSON arrays for quiz creation and uses a dedicated prompt execution via <code>/api/rag/generate-title</code> for automatic conversation titling.
</p>
<p>
  <b>4. Asynchronous Non-Blocking User Interface (Modern Web Frontend)</b><br>
  Features a high-fidelity dark-themed dashboard served directly via Spring Web MVC. Employs native JavaScript <code>fetch()</code> operations and <code>FormData</code> for seamless Drag & Drop file uploads, real-time message filtering, full-text history searching, dynamic quiz card wizards (equipped with regex-based JSON extraction), document preview modals, real-time response streaming via <code>Flux&lt;String&gt;</code>, and request cancellation via <code>AbortController</code>.
</p>

<h2>Installation & Configuration</h2>
<p>
  <b>1. Prerequisites</b><br>
  Ensure you have <b>JDK 26</b>, <b>Maven</b>, <b>Docker Desktop</b>, and <b>Ollama</b> installed on your environment.
</p>
<p>
  <b>2. Start PostgreSQL with pgvector</b><br>
  Run the PostgreSQL container with the <code>pgvector</code> extension enabled:
</p>
<pre><code>docker run -d --name docshelper-postgres -p 5432:5432 -e POSTGRES_DB=docshelper -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres pgvector/pgvector:pg16</code></pre>
<p>
  <b>3. Setup Local Models via Ollama</b><br>
  Pull the required LLM and embedding models in your terminal:
</p>
<pre><code>ollama pull llama3.1:8b
ollama pull bge-m3</code></pre>
<p>
  <b>4. Run the Application</b><br>
  Build and start the Spring Boot backend using Maven:
</p>
<pre><code>mvn clean spring-boot:run</code></pre>
<p>
  Once started, navigate to <code>http://localhost:8080</code> in your browser to access the DocsHelper dashboard.
</p>

<hr>

<h1>DOCSHELPER - ASYSTENT RAG</h1>
<p>
  Aplikacja to inteligentny asystent typu <b>RAG (Retrieval-Augmented Generation)</b> zbudowany przy użyciu ekosystemu <b>Spring Boot</b> oraz nowoczesnego frameworka <b>Spring AI</b>. Umożliwia prowadzenie kontekstowych rozmów z własnymi dokumentami w sposób lokalny i bezpieczny, realizację hybrydowego wyszukiwania w bazie wiedzy, podgląd źródeł odpowiedzi (cytowania) oraz automatyczne generowanie interaktywnych quizów i streszczeń.
</p>

<h2>Możliwości Systemu</h2>
<p>
  <b>Zasilanie Wiedzą:</b> Użytkownicy mogą wgrywać pliki bezpośrednio za pomocą nowoczesnego interfejsu webowego z obsługą przeciągnij-i-upuść (Drag & Drop), z automatyczną obsługą formatów PDF, DOCX, RTF i TXT.<br>
  <b>Indeksowanie Semantyczne i Inteligentny Chunking:</b> Treść dokumentów jest czyszczona, dzielona na zoptymalizowane fragmenty z zachowaniem nakładania się zdań (overlap) i zamieniana na wektory zapisywane bezpośrednio w bazie PostgreSQL za pomocą rozszerzenia <code>pgvector</code>.<br>
  <b>Silnik RAG z Wyszukiwaniem Hybrydowym:</b> Łączy wyszukiwanie semantyczne (wektorowe) z tradycyjnym wyszukiwaniem słów kluczowych w SQL. Automatycznie rozpoznaje intencję zapytania (Quiz, Streszczenie, Czat), dynamicznie dostosowując parametry, limity top-K, progi podobieństwa (odrzucanie szumu powyżej progu 0.55) oraz prompty systemowe.<br>
  <b>Trwałość Stanu i Zarządzanie JPA:</b> Pełna historia czatów oraz relacje wiadomości zarządzane są przez Spring Data JPA i trwale przechowywane w relacyjnej bazie danych PostgreSQL.<br>
  <b>Interaktywne Quizy, Cytowania i Generowanie Tytułów:</b> Wymusza czysty format JSON dla quizów, zwraca obiekty DTO z dokładnymi źródłami i fragmentami plików, strumieniuje odpowiedzi w czasie rzeczywistym (Server-Sent Events / Flux) oraz automatycznie tworzy zwięzłe tytuły rozmów (3-5 słów).<br>
  <b>100% Prywatności:</b> Napędzane lokalnym silnikiem LLM, co oznacza, że prywatne pliki i konwersacje nigdy nie opuszczają Twojego komputera.
</p>

<h2>Stos Technologiczny</h2>
<p>
  <b>Język:</b> Java 26<br>
  <b>Framework:</b> Spring Boot 4.1.0 (Web MVC, Spring Data JPA, RESTful APIs)<br>
  <b>Baza Danych i Magazyn Wektorowy:</b> PostgreSQL z rozszerzeniem <code>pgvector</code> (Kontener Docker)<br>
  <b>Architektura AI:</b> Spring AI 2.0.0 (Rdzeń oparty na wzorcu Builder, ChatClient, VectorStore, PgVectorStore, Document Transformers)<br>
  <b>Środowisko LLM:</b> Ollama (Lokalna usługa dla systemu macOS)<br>
  <b>Aktywne Modele:</b> Czat: <code>llama3.1:8b</code> | Wektory: <code>bge-m3</code><br>
  <b>Ekstrakcja Treści:</b> Apache Tika (poprzez <code>TikaDocumentReader</code>)<br>
  <b>Narzędzie budowania:</b> Maven
</p>

<h2>Architektura i Rozwiązania Techniczne</h2>
<p>
  <b>1. Semantyczny Magazyn Wiedzy i Trwałość Danej (PgVector & JPA)</b><br>
  Rejestruje komponent <code>PgVectorStore</code> połączony z bazą PostgreSQL w Dockerze. Encje (<code>Chat</code>, <code>Message</code>) oraz repozytoria (<code>ChatRepository</code>) wykorzystują Spring Data JPA do zapewnienia trwałego przechowywania historii czatów. Magazyn wektorowy natywnie obsługuje wielowymiarowe embeddingi w PostgreSQL dzięki rozszerzeniu <code>pgvector</code>, eliminując ulotność pamięci RAM i zapewniając pełną spójność ACID dla danych relacyjnych i wektorowych.
</p>
<p>
  <b>2. Automatyczne Przetwarzanie i Ekstrakcja Treści (Tika Ingestion Engine)</b><br>
  Warstwa wejściowa (<code>RagController</code> / <code>RagService</code>) przyjmuje paczki <code>MultipartFile</code> i wykorzystuje bibliotekę Apache Tika (<code>TikaDocumentReader</code>) do czyszczenia tekstu ze zbędnych znaczników. Dokumenty wzbogacane są o metadane (m.in. <code>file_name</code>) i dzielone przez odpowiednio skonfigurowany <code>TokenTextSplitter</code> z nakładaniem się fragmentów (rozmiar chunku: 800 tokenów, overlap: 350 znaków) przed zapisem w <code>vectorStore.add()</code>. System obsługuje również usuwanie wektorów z bazy (<code>deleteFileFromKnowledgeBase</code>) po skasowaniu dokumentu.
</p>
<p>
  <b>3. Semantyczne Wyszukiwanie i Inżynieria Kontekstowa (Hybrydowy RAG Pipeline)</b><br>
  Realizuje wyszukiwanie hybrydowe łączące dopasowanie wektorowe z tradycyjnym wyszukiwaniem fraz kluczowych w SQL (<code>findByKeyword</code>). Silnik dostosowuje parametry do intencji użytkownika:
  <br>• <b>Tryb Czatu:</b> Pobiera top 5 dopasowanych fragmentów ze zwiększonym progiem podobieństwa (<code>0.55</code>) w celu wyeliminowania szumu.
  <br>• <b>Tryby Quizu i Streszczenia:</b> Zwiększa głębokość do top 12 fragmentów (<code>topK(12)</code>) bez progu podobieństwa dla maksymalnego pokrycia wiedzy.
  <br>• <b>Filtrowanie Metadanych:</b> Buduje dynamiczne wyrażenia filtrujące (np. <code>file_name == 'doc.pdf' || ...</code>), ograniczając kontekst wyłącznie do plików zaznaczonych przez użytkownika.
  <br>• <b>Okno Przesuwne Historii i Cytowanie Źródeł:</b> Dołącza do kontekstu do 6 ostatnich wiadomości z historii konwersacji oraz pakuje wygenerowane odpowiedzi wraz z dokładnymi źródłami i fragmentami plików do strukturyzowanych obiektów DTO (<code>RagResponse</code>).
  <br>• <b>Strukturyzowane Wyjście i Auto-Tytuły:</b> Wymusza czysty schemat JSON dla interaktywnego quizu oraz korzysta z dedykowanego wywołania na punkcie <code>/api/rag/generate-title</code> do generowania zwięzłych tytułów czatów.
</p>
<p>
  <b>4. Asynchroniczny, Nieblokujący Interfejs Użytkownika (Nowoczesny Web Frontend)</b><br>
  Responsywny panel w ciemnym motywie, serwowany bezpośrednio przez Spring Web MVC. Korzysta z natywnych operacji <code>fetch()</code> i <code>FormData</code> do przesyłania plików metodą Drag & Drop, filtrowania wiadomości w czasie rzeczywistym, przeszukiwania historii czatów, podglądu dokumentów, dynamicznych kart quizowych (wyposażonych w odporną obsługę JSON przez Regex), strumieniowania odpowiedzi w czasie rzeczywistym za pomocą strumieni <code>Flux&lt;String&gt;</code> oraz natychmiastowego anulowania żądań poprzez <code>AbortController</code>.
</p>

<h2>Instalacja i Konfiguracja</h2>
<p>
  <b>1. Wymagania Wstępne</b><br>
  Upewnij się, że w Twoim środowisku zainstalowane są: <b>JDK 26</b>, <b>Maven</b>, <b>Docker Desktop</b> oraz silnik <b>Ollama</b>.
</p>
<p>
  <b>2. Uruchomienie Bazy PostgreSQL z rozszerzeniem pgvector</b><br>
  Uruchom kontener z bazą danych PostgreSQL z aktywną obsługą wektorów:
</p>
<pre><code>docker run -d --name docshelper-postgres -p 5432:5432 -e POSTGRES_DB=docshelper -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres pgvector/pgvector:pg16</code></pre>
<p>
  <b>3. Pobranie Modeli Lokalnych w Ollama</b><br>
  Pobierz wymagany model językowy oraz model tworzący embeddingi wektorowe:
</p>
<pre><code>ollama pull llama3.1:8b
ollama pull bge-m3</code></pre>
<p>
  <b>4. Uruchomienie Aplikacji</b><br>
  Skompiluj i uruchom aplikację Spring Boot przy użyciu Mavena:
</p>
<pre><code>mvn clean spring-boot:run</code></pre>
<p>
  Po pomyślnym uruchomieniu przejdź pod adres <code>http://localhost:8080</code> w przeglądarce, aby otworzyć panel aplikacji DocsHelper.
</p>