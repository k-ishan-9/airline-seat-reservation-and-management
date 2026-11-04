package com.arms.model;

/** A logged-in user as the rest of the app sees them. The password hash never leaves the DAO layer. */
public record User(long id, String username, String fullName, Role role) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
