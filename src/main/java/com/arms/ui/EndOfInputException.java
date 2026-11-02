package com.arms.ui;

/** Thrown when input ends (Ctrl+D / Ctrl+Z or a closed pipe), so the app can exit cleanly. */
public class EndOfInputException extends RuntimeException {
}
