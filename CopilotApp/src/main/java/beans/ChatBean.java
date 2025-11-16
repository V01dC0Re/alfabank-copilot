package beans;

import entities.*;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.UserTransaction;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import services.HuggingFaceService;

@Named
@SessionScoped
public class ChatBean implements Serializable {

    @PersistenceContext(unitName = "mainDB")
    private EntityManager em;

    @Inject
    private HuggingFaceService hfService;

    @Inject
    private UserTransaction utx;

    @Inject
    private LoginBean loginBean;

    private Chat currentChat;
    private String userInput;
    private String newChatTitle = "Новый чат";
    private boolean isLoading = false;
    private String lastError = null;

    public void createNewChat() {
        if (newChatTitle == null || newChatTitle.trim().isEmpty()) {
            newChatTitle = "Новый чат";
        }
        try {
            utx.begin();
            Chat chat = new Chat();
            chat.setTitle(newChatTitle.trim());
            chat.setUser(loginBean.getCurrentUser());
            em.persist(chat);
            utx.commit();
            currentChat = chat;
            newChatTitle = "Новый чат";
        } catch (Exception e) {
            rollback();
        }
    }

    public void sendMessage() {
        if (userInput == null || userInput.trim().isEmpty()) return;
        
        isLoading = true;
        lastError = null;
        String localUserInput = userInput.trim();
        userInput = "";

        try {
            if (currentChat == null) createNewChat();
            if (currentChat == null) throw new RuntimeException("Chat creation failed");

            utx.begin();
            saveMessageWithoutTx("user", localUserInput);
            utx.commit();

            String botResponse = hfService.getResponse(localUserInput);
            
            utx.begin();
            saveMessageWithoutTx("assistant", botResponse);
            utx.commit();
            
        } catch (Exception e) {
            String errorMsg = "Ошибка: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            if (errorMsg.length() > 200) errorMsg = errorMsg.substring(0, 197) + "...";
            lastError = errorMsg;
            
            try {
                utx.begin();
                saveMessageWithoutTx("assistant", errorMsg);
                utx.commit();
            } catch (Exception ex) {
                rollback();
            }
        } finally {
            isLoading = false;
        }
    }

    private void saveMessageWithoutTx(String role, String content) {
        Message msg = new Message();
        msg.setContent(content);
        msg.setRole(role);
        msg.setChat(currentChat);
        em.persist(msg);
    }

    public List<Message> getMessages() {
        if (currentChat == null || currentChat.getId() == null) return new ArrayList<>();
        TypedQuery<Message> query = em.createQuery(
                "SELECT m FROM Message m WHERE m.chat = :chat ORDER BY m.id ASC",
                Message.class
        );
        query.setParameter("chat", currentChat);
        return query.getResultList();
    }

    public List<Chat> getUserChats() {
        if (loginBean.getCurrentUser() == null) {
            return new ArrayList<>();
        }
        TypedQuery<Chat> query = em.createQuery(
                "SELECT c FROM Chat c WHERE c.user = :user ORDER BY c.id DESC",
                Chat.class
        );
        query.setParameter("user", loginBean.getCurrentUser());
        return query.getResultList();
    }

    public void switchToChat(Chat chat) {
        this.currentChat = chat;
    }

    private void rollback() {
        try {
            if (utx != null && utx.getStatus() == jakarta.transaction.Status.STATUS_ACTIVE) {
                utx.rollback();
            }
        } catch (Exception ex) {
        }
    }

    // Геттеры и сеттеры
    public boolean isLoading() {
        return isLoading;
    }
    
    public String getLastError() {
        return lastError;
    }

    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }

    public Chat getCurrentChat() { return currentChat; }

    public String getNewChatTitle() { return newChatTitle; }
    public void setNewChatTitle(String newChatTitle) { this.newChatTitle = newChatTitle; }
}