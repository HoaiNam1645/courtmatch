package com.courtly.seed;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.BalanceTransactionType;
import com.courtly.common.enums.FeeType;
import com.courtly.common.enums.PayoutStatus;
import com.courtly.common.enums.WithdrawalStatus;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.finance.OwnerBalanceTransaction;
import com.courtly.domain.finance.OwnerBalanceTransactionRepository;
import com.courtly.domain.finance.PayoutTransaction;
import com.courtly.domain.finance.PayoutTransactionRepository;
import com.courtly.domain.finance.PlatformFeeConfig;
import com.courtly.domain.finance.PlatformFeeConfigRepository;
import com.courtly.domain.finance.WithdrawalRequest;
import com.courtly.domain.finance.WithdrawalRequestRepository;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buoc 5: platform_fee_configs, owner_balance_transactions, withdrawal_requests,
 * payout_transactions.
 *
 * <p>So tien trong so cai duoc viet san theo muc phi 10% cua cac booking da thanh toan,
 * seeder khong tu tinh phi.
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class FinanceSeeder implements Seeder {

    private static final BigDecimal FEE_PERCENT = BigDecimal.valueOf(10);

    private final PlatformFeeConfigRepository feeConfigRepository;
    private final OwnerBalanceTransactionRepository balanceRepository;
    private final WithdrawalRequestRepository withdrawalRepository;
    private final PayoutTransactionRepository payoutRepository;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    /**
     * Mot dong so cai.
     *
     * @param bookingKey null neu bien dong khong gan voi booking nao, vi du rut tien
     * @param amount     duong la cong tien, am la tru tien
     */
    private record LedgerSeed(String key, String ownerKey, String bookingKey,
                              BalanceTransactionType type, long amount, long balanceAfter,
                              int minutesAgo, String note) {
    }

    private static final List<LedgerSeed> LEDGER = List.of(
            // owner-01: booking-01 (210.000) va booking-06 (210.000), phi 10% moi don.
            new LedgerSeed("owner-01-earn-1", "owner-01", "booking-01",
                    BalanceTransactionType.EARNING, 210_000, 210_000, 1_800,
                    "Doanh thu booking CT-20260924-0001"),
            new LedgerSeed("owner-01-fee-1", "owner-01", "booking-01",
                    BalanceTransactionType.PLATFORM_FEE, -21_000, 189_000, 1_799,
                    "Phi nen tang 10% booking CT-20260924-0001"),
            new LedgerSeed("owner-01-earn-2", "owner-01", "booking-06",
                    BalanceTransactionType.EARNING, 210_000, 399_000, 360,
                    "Doanh thu booking CT-20260925-0006"),
            new LedgerSeed("owner-01-fee-2", "owner-01", "booking-06",
                    BalanceTransactionType.PLATFORM_FEE, -21_000, 378_000, 359,
                    "Phi nen tang 10% booking CT-20260925-0006"),
            // Rut tien phai dung sau hai khoan doanh thu tren de balance_after lien mach.
            new LedgerSeed("owner-01-withdrawal", "owner-01", null,
                    BalanceTransactionType.WITHDRAWAL, -189_000, 189_000, 120,
                    "Rut tien ve tai khoan 0359111001"),
            // owner-02: booking-03 (140.000).
            new LedgerSeed("owner-02-earn-1", "owner-02", "booking-03",
                    BalanceTransactionType.EARNING, 140_000, 140_000, 17_280,
                    "Doanh thu booking CT-20260913-0003"),
            new LedgerSeed("owner-02-fee-1", "owner-02", "booking-03",
                    BalanceTransactionType.PLATFORM_FEE, -14_000, 126_000, 17_279,
                    "Phi nen tang 10% booking CT-20260913-0003"),
            // owner-03: booking-07 (95.000).
            new LedgerSeed("owner-03-earn-1", "owner-03", "booking-07",
                    BalanceTransactionType.EARNING, 95_000, 95_000, 31_680,
                    "Doanh thu booking CT-20260903-0007"),
            new LedgerSeed("owner-03-fee-1", "owner-03", "booking-07",
                    BalanceTransactionType.PLATFORM_FEE, -9_500, 85_500, 31_679,
                    "Phi nen tang 10% booking CT-20260903-0007"));

    @Override
    public String name() {
        return "platform fee + owner balance";
    }

    @Override
    @Transactional
    public void seed() {
        if (feeConfigRepository.count() > 0) {
            log.info("  [finance] da co du lieu, bo qua");
            return;
        }

        seedFeeConfig();
        seedLedger();
        seedWithdrawals();

        log.info("  [finance] phi {}%, {} dong so cai, 2 yeu cau rut tien", FEE_PERCENT, LEDGER.size());
    }

    private void seedFeeConfig() {
        PlatformFeeConfig config = new PlatformFeeConfig();
        config.setId(SeedIds.of("platform-fee:default"));
        config.setFeeType(FeeType.PERCENTAGE);
        config.setFeeValue(FEE_PERCENT);
        config.setStatus(ActiveStatus.ACTIVE);
        config.setEffectiveFrom(Instant.now().minus(90, ChronoUnit.DAYS));
        config.setCreatedBy(user("admin"));
        feeConfigRepository.save(config);
    }

    private void seedLedger() {
        for (LedgerSeed seed : LEDGER) {
            Booking booking = seed.bookingKey() == null ? null
                    : bookingRepository.findById(SeedIds.of("booking:" + seed.bookingKey())).orElseThrow();
            Payment payment = seed.bookingKey() == null ? null
                    : paymentRepository.findById(SeedIds.of("payment:" + seed.bookingKey())).orElseThrow();

            OwnerBalanceTransaction entry = new OwnerBalanceTransaction();
            entry.setId(SeedIds.of("balance:" + seed.key()));
            entry.setOwner(user(seed.ownerKey()));
            entry.setBooking(booking);
            entry.setPayment(payment);
            entry.setType(seed.type());
            entry.setAmount(BigDecimal.valueOf(seed.amount()));
            entry.setBalanceAfter(BigDecimal.valueOf(seed.balanceAfter()));
            entry.setNote(seed.note());
            entry.setCreatedAt(Instant.now().minus(seed.minutesAgo(), ChronoUnit.MINUTES));
            balanceRepository.save(entry);
        }
    }

    /** Mot yeu cau da chi tra xong va mot yeu cau dang cho admin duyet (2.3.45). */
    private void seedWithdrawals() {
        User admin = user("admin");
        User firstOwner = user("owner-01");
        Instant requestedAt = Instant.now().minus(5, ChronoUnit.HOURS);

        WithdrawalRequest paidRequest = newRequest("owner-01-paid", firstOwner,
                189_000, WithdrawalStatus.PAID, requestedAt);
        paidRequest.setReviewedBy(admin);
        paidRequest.setReviewedAt(requestedAt.plus(2, ChronoUnit.HOURS));
        withdrawalRepository.save(paidRequest);

        PayoutTransaction payout = new PayoutTransaction();
        payout.setId(SeedIds.of("payout:owner-01-paid"));
        payout.setWithdrawalRequest(paidRequest);
        payout.setOwner(firstOwner);
        payout.setAmount(BigDecimal.valueOf(189_000));
        payout.setProvider("sepay");
        payout.setProviderTransactionId("PAYOUT-OWNER01-0001");
        payout.setStatus(PayoutStatus.SUCCESS);
        payout.setCreatedAt(requestedAt.plus(2, ChronoUnit.HOURS));
        payout.setPaidAt(requestedAt.plus(3, ChronoUnit.HOURS));
        payoutRepository.save(payout);

        withdrawalRepository.save(newRequest("owner-02-pending", user("owner-02"),
                126_000, WithdrawalStatus.PENDING, Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    private WithdrawalRequest newRequest(String key, User owner, long amount,
                                         WithdrawalStatus status, Instant requestedAt) {
        WithdrawalRequest request = new WithdrawalRequest();
        request.setId(SeedIds.of("withdrawal:" + key));
        request.setOwner(owner);
        request.setAmount(BigDecimal.valueOf(amount));
        request.setBankName(owner.getCourtOwnerProfile().getBankName());
        request.setBankAccountNo(owner.getCourtOwnerProfile().getBankAccountNo());
        request.setBankAccountName(owner.getCourtOwnerProfile().getBankAccountName());
        request.setStatus(status);
        request.setRequestedAt(requestedAt);
        return request;
    }

    private User user(String key) {
        return userRepository.findById(SeedIds.of("user:" + key))
                .orElseThrow(() -> new IllegalStateException("Thieu tai khoan '" + key + "'"));
    }
}
