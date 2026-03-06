package com.example.event.service.Impl;

import com.example.event.service.MailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {
    private final JavaMailSender mailSender;
    public void sendMail(String[] to, String subject, String htmlContent, Map<String, File> attachments) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            if (attachments != null) {
                for (Map.Entry<String, File> entry : attachments.entrySet()) {
                    FileSystemResource res = new FileSystemResource(entry.getValue());
                    helper.addInline(entry.getKey(), res);
                }
            }

            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi gửi mail: " + e.getMessage());
        }
    }

    @Override
    public void registerUser(String verifyToken, String email) {
        String verifyLink = "http://localhost:8080/api/v1/auth/verify?verifyToken=" + verifyToken + "&email=" + email;
        String resendLink = "http://localhost:8080/api/v1/auth/resend-verify?email=" + email;
        String htmlContent = "<div style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; border: 1px solid #ddd; padding: 30px; border-radius: 20px;\">" +
                "    <h2 style=\"color: #6d44c4; text-align: center; font-size: 24px; font-weight: 700;\">Xác thực tài khoản của bạn</h2>" +
                "    <p>Xin chào <strong>" + email + "</strong>,</p>" +
                "    <p>Cảm ơn bạn đã tham gia hệ thống quản lý sự kiện. Vui lòng nhấn vào nút bên dưới để hoàn tất quá trình đăng ký:</p>" +
                "    " +
                "    <div style=\"text-align: center; margin: 35px 0;\">" +
                "        <a href=\"" + verifyLink + "\" style=\"background-color: #6d44c4; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; font-weight: 600; font-size: 16px; display: inline-block; transition: background-color 0.3s ease;\">Xác thực ngay</a>" +
                "    </div>" +
                "    " +
                "    <p style=\"font-size: 0.9rem; color: #888;\">Lưu ý: Liên kết này sẽ hết hạn sau <strong style=\"color: #5a37a0;\">5 phút</strong>.</p>" +
                "    " +
                "    <hr style=\"border: 0; border-top: 1px solid #eee; margin: 25px 0;\">" +
                "    " +
                "    <p style=\"font-size: 0.9rem;\">Nếu bạn chưa nhận được email hoặc link đã hết hạn, bạn có thể yêu cầu gửi lại:</p>" +
                "    <div style=\"text-align: center; margin-top: 15px;\">" +
                "        <a href=\"" + resendLink + "\" style=\"color: #6d44c4; text-decoration: none; font-weight: 600; font-size: 0.9rem;\">Gửi lại email xác thực</a>" +
                "    </div>" +
                "    " +
                "    <p style=\"font-size: 0.8rem; color: #bbb; margin-top: 40px; text-align: center;\">Trân trọng,<br>Đội ngũ hỗ trợ kỹ thuật.</p>" +
                "</div>";
        sendMail(new String[]{email}, "Xác thực tài khoản", htmlContent, null);
    }
}
