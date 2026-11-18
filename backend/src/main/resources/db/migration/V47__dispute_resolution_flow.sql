-- 2.3.102 - 2.3.111: tam hoan hoa hong, theo doi hoan tien truc tiep, tu dong xu thua, tam ngung san.

ALTER TABLE booking_commissions
    ADD COLUMN status_before varchar(30),
    ADD COLUMN suspended_at timestamptz,
    ADD COLUMN suspended_dispute_id uuid REFERENCES booking_disputes (id) ON DELETE SET NULL;

ALTER TABLE refunds
    ADD COLUMN owner_note text,
    ADD COLUMN player_complaint text,
    ADD COLUMN complained_at timestamptz,
    ADD COLUMN auto_confirmed boolean NOT NULL DEFAULT false;

ALTER TABLE booking_disputes
    ADD COLUMN owner_reminded_at timestamptz;

ALTER TABLE venues
    ADD COLUMN suspension_reason text,
    ADD COLUMN suspended_at timestamptz;

CREATE INDEX bd_overdue_idx ON booking_disputes (owner_response_deadline)
    WHERE status = 'awaiting_owner_response';
CREATE INDEX refunds_dispute_idx ON refunds (dispute_id) WHERE dispute_id IS NOT NULL;
