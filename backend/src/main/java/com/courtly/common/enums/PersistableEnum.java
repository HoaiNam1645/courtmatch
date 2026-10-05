package com.courtly.common.enums;

/**
 * Enum duoc luu xuong database bang gia tri chuoi thuong (snake_case),
 * dung dung gia tri trong CHECK constraint cua migration thay vi ten hang Java.
 */
public interface PersistableEnum {

    String getValue();
}
