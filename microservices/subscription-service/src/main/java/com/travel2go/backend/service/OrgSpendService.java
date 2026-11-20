package com.travel2go.backend.service;

import com.travel2go.backend.model.Approval;
import com.travel2go.backend.model.CreditAccount;
import com.travel2go.backend.model.Org;
import com.travel2go.backend.model.OrgBooking;
import com.travel2go.backend.model.OrgMember;
import com.travel2go.backend.model.SpendPolicy;
import com.travel2go.backend.repository.ApprovalRepository;
import com.travel2go.backend.repository.CreditAccountRepository;
import com.travel2go.backend.repository.OrgBookingRepository;
import com.travel2go.backend.repository.OrgMemberRepository;
import com.travel2go.backend.repository.OrgRepository;
import com.travel2go.backend.repository.SpendPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Business (F6) spend controls, approvals, credit line and the consolidated monthly
 * bill. This is a B2B subscription + credit, entirely separate from the always-free
 * consumer booking path (G1). A booking over policy cannot confirm without approval
 * (fail-closed); credit draws can never exceed the limit.
 */
@Service
@RequiredArgsConstructor
public class OrgSpendService {

    public enum Decision { APPROVED, PENDING_APPROVAL, DENIED }

    private static final Set<String> APPROVER_ROLES = Set.of("ADMIN", "APPROVER");

    private final OrgRepository orgRepository;
    private final OrgMemberRepository memberRepository;
    private final SpendPolicyRepository policyRepository;
    private final CreditAccountRepository creditRepository;
    private final ApprovalRepository approvalRepository;
    private final OrgBookingRepository orgBookingRepository;

    @Transactional
    public Org createOrg(String name, String adminUserId, String namedContact) {
        Org org = orgRepository.save(Org.builder()
                .name(name).status("ACTIVE").namedContact(namedContact).createdAt(new Date()).build());
        memberRepository.save(OrgMember.builder()
                .orgId(org.getId()).userId(adminUserId).role("ADMIN").build());
        creditRepository.save(CreditAccount.builder()
                .orgId(org.getId()).limitPaise(0L).outstandingPaise(0L).build());
        return org;
    }

    @Transactional
    public OrgMember addMember(UUID orgId, String actingUserId, String userId, String role) {
        requireRole(orgId, actingUserId, Set.of("ADMIN"));
        OrgMember member = memberRepository.findByOrgIdAndUserId(orgId, userId)
                .orElse(OrgMember.builder().orgId(orgId).userId(userId).build());
        member.setRole(role);
        return memberRepository.save(member);
    }

    @Transactional
    public SpendPolicy setPolicy(UUID orgId, String actingUserId, long perTripCapPaise, long monthlyCapPaise) {
        requireRole(orgId, actingUserId, Set.of("ADMIN"));
        return policyRepository.save(SpendPolicy.builder()
                .orgId(orgId).perTripCapPaise(perTripCapPaise).monthlyCapPaise(monthlyCapPaise).build());
    }

    @Transactional
    public CreditAccount setCreditLimit(UUID orgId, String actingUserId, long limitPaise) {
        requireRole(orgId, actingUserId, Set.of("ADMIN"));
        CreditAccount account = creditRepository.findById(orgId)
                .orElse(CreditAccount.builder().orgId(orgId).outstandingPaise(0L).build());
        account.setLimitPaise(limitPaise);
        return creditRepository.save(account);
    }

    /**
     * Evaluate a booking against the org's spend policy and credit line. Under the caps
     * it confirms immediately (drawing credit); over a cap it is held PENDING_APPROVAL
     * and must not confirm until approved; a credit-limit breach is DENIED.
     */
    @Transactional
    public Decision evaluateBooking(UUID orgId, String memberUserId, String bookingRef, long amountPaise) {
        requireMember(orgId, memberUserId);

        // Idempotent: already confirmed or already pending.
        if (orgBookingRepository.findByOrgIdAndBookingRef(orgId, bookingRef).isPresent()) {
            return Decision.APPROVED;
        }
        Optional<Approval> existing = approvalRepository.findByOrgIdAndBookingRef(orgId, bookingRef);
        if (existing.isPresent() && "PENDING".equals(existing.get().getStatus())) {
            return Decision.PENDING_APPROVAL;
        }

        if (overCap(orgId, amountPaise)) {
            approvalRepository.save(Approval.builder()
                    .orgId(orgId).bookingRef(bookingRef).memberUserId(memberUserId)
                    .amountPaise(amountPaise).status("PENDING").createdAt(new Date()).build());
            return Decision.PENDING_APPROVAL;
        }
        return confirm(orgId, bookingRef, amountPaise);
    }

    /** An approver clears a held booking; it then confirms (subject to the credit limit). */
    @Transactional
    public Decision approve(UUID orgId, String bookingRef, String approverUserId) {
        requireRole(orgId, approverUserId, APPROVER_ROLES);
        Approval approval = approvalRepository.findByOrgIdAndBookingRef(orgId, bookingRef)
                .orElseThrow(() -> new IllegalArgumentException("No approval pending for " + bookingRef));
        // Separation of duties: the booker cannot approve their own over-cap booking.
        if (approverUserId.equals(approval.getMemberUserId())) {
            throw new AccessDeniedException("An approver cannot approve their own booking");
        }
        if ("APPROVED".equals(approval.getStatus())
                && orgBookingRepository.findByOrgIdAndBookingRef(orgId, bookingRef).isPresent()) {
            return Decision.APPROVED; // idempotent
        }
        approval.setStatus("APPROVED");
        approval.setDecidedBy(approverUserId);
        approvalRepository.save(approval);
        return confirm(orgId, bookingRef, approval.getAmountPaise());
    }

    public long consolidatedBillPaise(UUID orgId, String period) {
        return orgBookingRepository.sumForPeriod(orgId, period);
    }

    public List<OrgBooking> bookingsForPeriod(UUID orgId, String period) {
        return orgBookingRepository.findByOrgIdAndPeriod(orgId, period);
    }

    public CreditAccount credit(UUID orgId) {
        return creditRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("No credit account for org " + orgId));
    }

    /** Authorize a read of an org's billing data: the caller must be a member. */
    public void assertMember(UUID orgId, String userId) {
        requireMember(orgId, userId);
    }

    private Decision confirm(UUID orgId, String bookingRef, long amountPaise) {
        if (orgBookingRepository.findByOrgIdAndBookingRef(orgId, bookingRef).isPresent()) {
            return Decision.APPROVED; // already confirmed
        }
        CreditAccount credit = creditRepository.findById(orgId).orElse(null);
        if (credit != null && credit.getLimitPaise() > 0
                && credit.getOutstandingPaise() + amountPaise > credit.getLimitPaise()) {
            return Decision.DENIED; // would exceed the credit limit
        }
        orgBookingRepository.save(OrgBooking.builder()
                .orgId(orgId).bookingRef(bookingRef).amountPaise(amountPaise)
                .period(currentPeriod()).createdAt(new Date()).build());
        if (credit != null && credit.getLimitPaise() > 0) {
            credit.setOutstandingPaise(credit.getOutstandingPaise() + amountPaise);
            creditRepository.save(credit);
        }
        return Decision.APPROVED;
    }

    private boolean overCap(UUID orgId, long amountPaise) {
        Optional<SpendPolicy> policy = policyRepository.findById(orgId);
        if (policy.isEmpty()) {
            return false; // no policy configured -> no cap gate
        }
        SpendPolicy p = policy.get();
        if (amountPaise > p.getPerTripCapPaise()) {
            return true;
        }
        long monthToDate = orgBookingRepository.sumForPeriod(orgId, currentPeriod());
        return monthToDate + amountPaise > p.getMonthlyCapPaise();
    }

    private OrgMember requireMember(UUID orgId, String userId) {
        return memberRepository.findByOrgIdAndUserId(orgId, userId)
                .orElseThrow(() -> new AccessDeniedException(userId + " is not a member of org " + orgId));
    }

    private void requireRole(UUID orgId, String userId, Set<String> roles) {
        OrgMember member = requireMember(orgId, userId);
        if (!roles.contains(member.getRole())) {
            throw new AccessDeniedException(userId + " lacks role " + roles + " in org " + orgId);
        }
    }

    private String currentPeriod() {
        return YearMonth.now().toString(); // YYYY-MM
    }
}
