package com.courtly.api.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationRepository;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

/** Nen chung cho test thong bao sinh ra tu nghiep vu: chu san, san, nguoi choi va ham thao tac / doc thong bao. */
abstract class NotificationFlowTestBase extends OwnerApiTestBase {

    @Autowired protected NotificationRepository notificationRepository;
    @Autowired protected PaymentRepository paymentRepository;

    protected User owner;
    protected User player;
    protected Venue venue;
    protected Court court;
    protected String ownerToken;
    protected String playerToken;
    protected int slot;

    @BeforeEach
    void setUpFlow() throws Exception {
        ensureRoles();
        owner = persistOwner("flow-owner");
        player = persistUser("flow-player", Role.PLAYER);
        venue = persistVenue(owner, "San Thong Bao");
        court = venue.getCourts().getFirst();
        ownerToken = loginAs(owner);
        playerToken = loginAs(player);
    }

    /** Thong bao cua mot nguoi dung, moi nhat truoc. */
    protected List<Notification> notificationsOf(User user) {
        return notificationRepository.findForUser(user.getId(), null, false, PageRequest.of(0, 50)).getContent();
    }

    protected List<Notification> notificationsOf(User user, String type) {
        return notificationRepository.findForUser(user.getId(), type, false, PageRequest.of(0, 50)).getContent();
    }

    protected String newBooking() throws Exception {
        return createBooking(playerToken, court, nextMonday(), LocalTime.of(6, 0).plusMinutes(100L * slot++));
    }

    protected void accept(String bookingId) throws Exception {
        mockMvcPost("/api/v1/owner/booking-requests/" + bookingId + "/accept", ownerToken, null, 200);
    }

    protected Payment paymentOf(String bookingId) {
        return paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(UUID.fromString(bookingId)).orElseThrow();
    }

    protected void reportPaid(String bookingId) throws Exception {
        mockMvcPost("/api/v1/payments/" + paymentOf(bookingId).getId() + "/report-paid", playerToken, "{\"method\":\"cash\"}", 200);
    }

    protected void mockMvcPost(String path, String token, String body, int expected) throws Exception {
        var request = post(path).header("Authorization", bearer(token));
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mockMvc.perform(request).andExpect(status().is(expected));
    }
}
