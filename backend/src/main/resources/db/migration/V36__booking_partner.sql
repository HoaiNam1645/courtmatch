-- B5: booking dat tu ket qua ghep cap ghi nho nguoi choi cung de goi y khi ghi tran dau (matches.booking_id).
ALTER TABLE bookings
    ADD COLUMN partner_user_id uuid REFERENCES users (id) ON DELETE SET NULL;
