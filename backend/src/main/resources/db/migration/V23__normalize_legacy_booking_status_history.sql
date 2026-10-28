-- V15 renamed the active booking state but legacy history rows retained the old value.
UPDATE booking_status_history
SET old_status = 'awaiting_payment'
WHERE old_status = 'pending_payment';

UPDATE booking_status_history
SET new_status = 'awaiting_payment'
WHERE new_status = 'pending_payment';
