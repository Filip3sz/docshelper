package code.filipesz.docshelper.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chats")
public class Chat {

    @Id
    private String id;

    private String title;

    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chat_selected_files", joinColumns = @JoinColumn(name = "chat_id"))
    @Column(name = "file_name")
    private List<String> selectedFiles = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "chat_id")
    private List<Message> messages = new ArrayList<>();

    public Chat() {
        this.createdAt = LocalDateTime.now();
    }

    public Chat(String id, String title) {
        this.id = id;
        this.title = title;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<String> getSelectedFiles() { return selectedFiles; }
    public void setSelectedFiles(List<String> selectedFiles) { this.selectedFiles = selectedFiles; }

    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }

    public void addMessage(Message message) {
        this.messages.add(message);
    }
}