package com.courtly.service.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courtly.common.config.MailProperties;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Kiem tra viec dung email, khong goi SMTP that.
 *
 * <p>Co test nay vi loi dau tien cua MailService khong lo ra o test controller: DTO va
 * service deu dung, nhung MimeMessageHelper duoc tao sai che do multipart nen mail khong
 * bao gio gui di - chi chay that moi phat hien.
 */
class MailServiceTest {

    private static final MailProperties ENABLED =
            new MailProperties(true, "no-reply@courtly.vn", "Courtly");

    private static MimeMessage emptyMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    /** Ban HTML nam sau vai lop multipart lien nhau, phai duyet de lay dung phan can kiem tra. */
    private static String htmlPart(Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                String found = htmlPart(multipart.getBodyPart(index));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String render(MimeMessage message) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        message.writeTo(buffer);
        return buffer.toString();
    }

    @Test
    @DisplayName("Email gui di chua ma xac minh, co ca ban text lan ban HTML")
    void sendsMailWithCode() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = emptyMessage();
        when(sender.createMimeMessage()).thenReturn(message);

        new MailService(sender, ENABLED).onPasswordResetCodeIssued(
                new PasswordResetCodeIssued("nguoi.dung@example.com", "Nguyen Van A", "123456", 10));

        verify(sender).send(any(MimeMessage.class));
        assertThat(render(message)).contains("123456").contains("multipart/alternative");
        assertThat(message.getSubject()).contains("123456");
        assertThat(message.getAllRecipients()[0]).hasToString("nguoi.dung@example.com");
    }

    @Test
    @DisplayName("Tat mail: khong dung toi SMTP")
    void doesNotSendWhenDisabled() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new MailService(sender, new MailProperties(false, "no-reply@courtly.vn", "Courtly"))
                .onPasswordResetCodeIssued(new PasswordResetCodeIssued(
                        "nguoi.dung@example.com", "Nguyen Van A", "123456", 10));

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Ho ten do nguoi dung tu nhap phai duoc escape trong ban HTML")
    void escapesUserSuppliedName() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = emptyMessage();
        when(sender.createMimeMessage()).thenReturn(message);

        new MailService(sender, ENABLED).onPasswordResetCodeIssued(new PasswordResetCodeIssued(
                "nguoi.dung@example.com", "<script>alert(1)</script>", "123456", 10));

        // Ban text thuan giu nguyen ho ten (khong ai hien thi no nhu HTML);
        // chi ban HTML moi bat buoc phai escape.
        // Chua saveChanges thi cac phan multipart chua duoc gan vao message, getContent tra null.
        message.saveChanges();
        assertThat(htmlPart(message)).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("SMTP loi: chi ghi log, khong nem nguoc ra luong goi")
    void swallowsSendFailure() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenThrow(new IllegalStateException("SMTP hong"));

        new MailService(sender, ENABLED).onPasswordResetCodeIssued(
                new PasswordResetCodeIssued("nguoi.dung@example.com", "Nguyen Van A", "123456", 10));
    }
}
