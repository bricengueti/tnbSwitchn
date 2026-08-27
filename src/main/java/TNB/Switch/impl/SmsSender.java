package TNB.Switch.impl;

public interface SmsSender {
    void send(String phoneNumber, String message);
}