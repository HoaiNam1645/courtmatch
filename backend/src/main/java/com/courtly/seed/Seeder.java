package com.courtly.seed;

/**
 * Mot buoc nap du lieu mau. Cac buoc chay theo thu tu {@code @Order} tang dan.
 *
 * <p>Moi seeder phai idempotent: chay lai nhieu lan khong tao du lieu trung.
 */
public interface Seeder {

    /** Ten hien thi trong log. */
    String name();

    void seed();
}
