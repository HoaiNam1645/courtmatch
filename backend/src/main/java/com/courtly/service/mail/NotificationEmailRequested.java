package com.courtly.service.mail;

/**
 * Can gui email cho mot thong bao (2.3.53 - 2.3.60); chi phat khi nguoi nhan bat kenh email.
 *
 * @param email    dia chi nhan
 * @param fullName ten nguoi nhan, dung de xung ho
 * @param title    tieu de thong bao, dung lam tieu de email
 * @param content  noi dung thong bao (co the null)
 */
public record NotificationEmailRequested(String email, String fullName, String title, String content) {
}
