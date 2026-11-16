-- 2.3.49: khoan thanh toan do quan tri vien ghi nhan truc tiep (chu san tra ngoai he thong, khong gui bao cao).
ALTER TABLE commission_payment_reports
    ADD COLUMN recorded_by uuid REFERENCES users (id) ON DELETE SET NULL;
COMMENT ON COLUMN commission_payment_reports.recorded_by IS 'Khac NULL khi quan tri vien tu ghi nhan giao dich thay chu san';
