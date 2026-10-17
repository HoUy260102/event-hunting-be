package com.example.event.service.Impl;

import com.example.event.service.MailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {
    private final JavaMailSender mailSender;

    @Value("${app.api-base-url:http://localhost:8080/api/v1}")
    private String apiBaseUrl;
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
            log.info("[MAIL] Gửi email thành công: to={}, subject={}", Arrays.toString(to), subject);
        } catch (Exception e) {
            log.error("[MAIL] Lỗi khi gửi email: to={}, subject={}, error={}", Arrays.toString(to), subject, e.getMessage(), e);
            throw new RuntimeException("Lỗi gửi mail: " + e.getMessage());
        }
    }

    @Override
    public void registerUser(String verifyToken, String email) {
        String baseUrl = (apiBaseUrl != null && !apiBaseUrl.isBlank())
                ? apiBaseUrl.replaceAll("/+$", "")
                : "http://localhost:8080/api/v1";
        String verifyLink = baseUrl + "/auth/verify?verifyToken=" + verifyToken + "&email=" + email;
        String resendLink = baseUrl + "/auth/resend-verify?email=" + email;
        String htmlContent = "<div style=\"background-color: #f8fafc; padding: 40px 15px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\">" +
                "  <div style=\"max-width: 540px; margin: 0 auto; background-color: #ffffff; border-radius: 18px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.06); border: 1px solid #e2e8f0;\">" +
                "    <!-- Header Banner -->" +
                "    <div style=\"background: linear-gradient(135deg, #10b981 0%, #059669 100%); padding: 32px 24px; text-align: center;\">" +
                "      <h1 style=\"color: #ffffff; margin: 0; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;\">Event Hunting</h1>" +
                "      <p style=\"color: rgba(255,255,255,0.92); margin: 6px 0 0 0; font-size: 14.5px;\">Xác thực tài khoản đăng ký</p>" +
                "    </div>" +
                "    <!-- Body Content -->" +
                "    <div style=\"padding: 32px 28px; color: #334155; line-height: 1.6;\">" +
                "      <p style=\"font-size: 15px; margin-top: 0;\">Xin chào <strong>" + email + "</strong>,</p>" +
                "      <p style=\"font-size: 14.5px; color: #475569;\">Cảm ơn bạn đã đăng ký tài khoản tại <strong>Event Hunting</strong>. Vui lòng nhấn vào nút bên dưới để xác thực địa chỉ email và hoàn tất quá trình đăng ký:</p>" +
                "      <!-- CTA Button Box -->" +
                "      <div style=\"margin: 28px 0; text-align: center;\">" +
                "        <a href=\"" + verifyLink + "\" style=\"display: inline-block; background: linear-gradient(135deg, #10b981 0%, #059669 100%); color: #ffffff; padding: 14px 36px; text-decoration: none; border-radius: 12px; font-weight: 700; font-size: 15px; box-shadow: 0 4px 14px rgba(16, 185, 129, 0.4); letter-spacing: 0.3px;\">Xác thực tài khoản ngay</a>" +
                "        <p style=\"margin: 12px 0 0 0; font-size: 13px; color: #64748b;\">Liên kết có hiệu lực trong vòng <strong style=\"color: #ef4444;\">5 phút</strong>.</p>" +
                "      </div>" +
                "      <!-- Security & Resend Notice -->" +
                "      <div style=\"background-color: #f8fafc; border-left: 4px solid #10b981; border-radius: 6px; padding: 12px 16px; margin: 24px 0;\">" +
                "        <p style=\"margin: 0; font-size: 13px; color: #64748b;\">ℹ️ Nếu liên kết đã hết hạn hoặc bạn không nhận được mail, bạn có thể <a href=\"" + resendLink + "\" style=\"color: #059669; font-weight: 600; text-decoration: underline;\">yêu cầu gửi lại email xác thực</a>.</p>" +
                "      </div>" +
                "      <p style=\"font-size: 13px; color: #94a3b8; margin-bottom: 0;\">Nếu bạn không đăng ký tài khoản này trên Event Hunting, vui lòng bỏ qua email. Tài khoản sẽ không được kích hoạt nếu chưa xác thực.</p>" +
                "    </div>" +
                "    <!-- Footer -->" +
                "    <div style=\"background-color: #f1f5f9; padding: 18px 24px; text-align: center; border-top: 1px solid #e2e8f0;\">" +
                "      <p style=\"margin: 0; font-size: 12px; color: #94a3b8;\">© " + java.time.Year.now().getValue() + " Event Hunting. All rights reserved.</p>" +
                "    </div>" +
                "  </div>" +
                "</div>";
        sendMail(new String[]{email}, "Xác thực tài khoản đăng ký - Event Hunting", htmlContent, null);
    }

    @Override
    public void sendForgotPasswordOtp(String email, String otp) {
        String htmlContent = "<div style=\"background-color: #f8fafc; padding: 40px 15px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\">" +
                "  <div style=\"max-width: 540px; margin: 0 auto; background-color: #ffffff; border-radius: 18px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.06); border: 1px solid #e2e8f0;\">" +
                "    <!-- Header Banner -->" +
                "    <div style=\"background: linear-gradient(135deg, #10b981 0%, #059669 100%); padding: 32px 24px; text-align: center;\">" +
                "      <h1 style=\"color: #ffffff; margin: 0; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;\">Event Hunting</h1>" +
                "      <p style=\"color: rgba(255,255,255,0.92); margin: 6px 0 0 0; font-size: 14.5px;\">Yêu cầu đặt lại mật khẩu</p>" +
                "    </div>" +
                "    <!-- Body Content -->" +
                "    <div style=\"padding: 32px 28px; color: #334155; line-height: 1.6;\">" +
                "      <p style=\"font-size: 15px; margin-top: 0;\">Xin chào <strong>" + email + "</strong>,</p>" +
                "      <p style=\"font-size: 14.5px; color: #475569;\">Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản Event Hunting của bạn. Vui lòng sử dụng mã xác thực (OTP) bên dưới để tiếp tục:</p>" +
                "      <!-- OTP Box -->" +
                "      <div style=\"margin: 28px 0; text-align: center;\">" +
                "        <div style=\"display: inline-block; background-color: #ecfdf5; border: 2px dashed #10b981; border-radius: 14px; padding: 16px 36px;\">" +
                "          <span style=\"font-size: 34px; font-weight: 800; letter-spacing: 8px; color: #059669; font-family: 'Courier New', Courier, monospace;\">" + otp + "</span>" +
                "        </div>" +
                "        <p style=\"margin: 10px 0 0 0; font-size: 13px; color: #64748b;\">Mã có hiệu lực trong vòng <strong style=\"color: #ef4444;\">5 phút</strong>.</p>" +
                "      </div>" +
                "      <!-- Security Notice -->" +
                "      <div style=\"background-color: #f8fafc; border-left: 4px solid #10b981; border-radius: 6px; padding: 12px 16px; margin: 24px 0;\">" +
                "        <p style=\"margin: 0; font-size: 13px; color: #64748b;\">🔒 <strong>Lưu ý bảo mật:</strong> Tuyệt đối không chia sẻ mã này với bất kỳ ai để bảo vệ an toàn cho tài khoản của bạn.</p>" +
                "      </div>" +
                "      <p style=\"font-size: 13px; color: #94a3b8; margin-bottom: 0;\">Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email. Mật khẩu của bạn vẫn an toàn và không bị thay đổi.</p>" +
                "    </div>" +
                "    <!-- Footer -->" +
                "    <div style=\"background-color: #f1f5f9; padding: 18px 24px; text-align: center; border-top: 1px solid #e2e8f0;\">" +
                "      <p style=\"margin: 0; font-size: 12px; color: #94a3b8;\">© " + java.time.Year.now().getValue() + " Event Hunting. All rights reserved.</p>" +
                "    </div>" +
                "  </div>" +
                "</div>";
        sendMail(new String[]{email}, "Mã xác thực đặt lại mật khẩu - Event Hunting", htmlContent, null);
    }
}
