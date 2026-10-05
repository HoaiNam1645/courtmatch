package com.courtly.service.mail;

import com.courtly.common.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gui email cua he thong.
 *
 * <p>Chay sau khi transaction commit va o luong khac: gui mail la loi goi ra ngoai,
 * khong duoc giu transaction ghi database va khong duoc bat nguoi dung cho SMTP tra loi.
 *
 * <p>Gui that bai thi chi ghi log. Nguoi dung da nhan phan hoi tu truoc do va van co the
 * bam "Gui lai ma"; bao loi luc nay cung khong giup ho lam gi khac.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    // --- Xac minh email khi dang ky (2.1.1) --------------------------------

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailVerificationCodeIssued(EmailVerificationCodeIssued event) {
        send(event.email(), event.code(),
                event.code() + " la ma xac minh tai khoan Courtly cua ban",
                """
                Xin chao %s,

                Cam on ban da dang ky Courtly. Ma xac minh tai khoan cua ban la: %s
                Ma co hieu luc trong %d phut.

                Nhap ma nay tren trang dang ky de kich hoat tai khoan.
                Neu ban khong dang ky Courtly, hay bo qua email nay.
                """.formatted(event.fullName(), event.code(), event.ttlMinutes()),
                htmlCard(event.fullName(), event.code(), event.ttlMinutes(),
                        "Nhập mã bên dưới để kích hoạt tài khoản Courtly của bạn.",
                        "Nếu bạn không đăng ký Courtly, hãy bỏ qua email này. "
                                + "Không chia sẻ mã cho bất kỳ ai."),
                "ma xac minh email");
    }

    // --- Dat lai mat khau (2.1.5) ------------------------------------------

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetCodeIssued(PasswordResetCodeIssued event) {
        send(event.email(), event.code(),
                event.code() + " la ma xac minh Courtly cua ban",
                """
                Xin chao %s,

                Ma xac minh de dat lai mat khau Courtly cua ban la: %s
                Ma co hieu luc trong %d phut.

                Neu ban khong yeu cau dat lai mat khau, hay bo qua email nay.
                Khong chia se ma nay voi bat ky ai, ke ca nguoi tu xung la nhan vien Courtly.
                """.formatted(event.fullName(), event.code(), event.ttlMinutes()),
                htmlCard(event.fullName(), event.code(), event.ttlMinutes(),
                        "Dùng mã bên dưới để xác minh danh tính và đặt lại mật khẩu.",
                        "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này. "
                                + "Không chia sẻ mã cho bất kỳ ai, kể cả người tự xưng là nhân viên Courtly."),
                "ma dat lai mat khau");
    }

    // --- Dung chung --------------------------------------------------------

    private void send(String to, String code, String subject, String plainText, String html,
                      String label) {
        if (!properties.enabled()) {
            // Moi truong chua cau hinh SMTP (hoac dang chay test): in ma ra log de con thu duoc.
            log.warn("MAIL TAT - {} cho {} la {}", label, to, code);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart = true: bat buoc khi gui kem ca ban text thuan lan ban HTML.
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText, html);

            mailSender.send(message);
            log.info("Da gui {} toi {}", label, to);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            // Bat rong: day la luong chay nen, loi thoat ra ngoai chi thanh mot dong
            // stacktrace vo thua vi va khong ai xu ly duoc.
            log.error("Khong gui duoc {} toi {}", label, to, e);
        }
    }

    /** Khung email dung chung cho moi loai ma OTP, chi khac hai doan chu. */
    private String htmlCard(String fullName, String code, int ttlMinutes,
                            String intro, String warning) {
        return """
                <div style="margin:0;padding:24px;background:#f5f5f4;font-family:'Segoe UI',Roboto,Arial,sans-serif">
                  <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e7e5e4">
                    <div style="background:#047857;padding:20px 28px">
                      <span style="color:#ffffff;font-size:18px;font-weight:700;letter-spacing:.5px">Courtly</span>
                    </div>
                    <div style="padding:28px">
                      <p style="margin:0 0 6px;font-size:15px;color:#1c1917">Xin chào <strong>%s</strong>,</p>
                      <p style="margin:0 0 20px;font-size:14px;line-height:22px;color:#57534e">%s</p>
                      <div style="margin:0 0 20px;padding:18px;text-align:center;background:#ecfdf5;border:1px solid #a7f3d0;border-radius:10px">
                        <span style="font-size:34px;font-weight:700;letter-spacing:10px;color:#065f46">%s</span>
                      </div>
                      <p style="margin:0 0 18px;font-size:13px;color:#57534e">
                        Mã có hiệu lực trong <strong>%d phút</strong>.
                      </p>
                      <p style="margin:0;padding:14px;background:#fffbeb;border:1px solid #fde68a;border-radius:8px;font-size:12px;line-height:20px;color:#92400e">%s</p>
                    </div>
                  </div>
                </div>
                """.formatted(escape(fullName), intro, code, ttlMinutes, warning);
    }

    /** Ten nguoi dung do nguoi dung tu nhap, khong duoc chen thang vao HTML. */
    private static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
