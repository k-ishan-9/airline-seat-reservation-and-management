package com.arms.ui;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * All keyboard input goes through here, and every read re-asks until the input is valid,
 * so a typo never crashes the program (the old version recursed into menus on bad input).
 */
public class ConsoleIO {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BufferedReader in;
    private final PrintStream out;
    private final Console console;

    public ConsoleIO(java.io.InputStream in, PrintStream out, Console console) {
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
        this.console = console;
    }

    public void println(String text) {
        out.println(text);
    }

    public void println() {
        out.println();
    }

    public void printf(String format, Object... args) {
        out.printf(format, args);
    }

    public String ask(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            String line = in.readLine();
            if (line == null) throw new EndOfInputException();
            return line.trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String askRequired(String prompt) {
        while (true) {
            String value = ask(prompt);
            if (!value.isEmpty()) return value;
            println("  Please enter a value.");
        }
    }

    public int askInt(String prompt, int min, int max) {
        while (true) {
            String value = ask(prompt);
            try {
                int n = Integer.parseInt(value);
                if (n >= min && n <= max) return n;
            } catch (NumberFormatException ignored) {
                // fall through to the hint
            }
            println("  Enter a number from " + min + " to " + max + ".");
        }
    }

    /** Accepts yyyy-MM-dd, "today", "tomorrow" or "+N" days. Empty input gives the default. */
    public LocalDate askDate(String prompt, LocalDate today, LocalDate defaultValue) {
        while (true) {
            String value = ask(prompt).toLowerCase();
            try {
                if (value.isEmpty()) return defaultValue;
                if (value.equals("today")) return today;
                if (value.equals("tomorrow")) return today.plusDays(1);
                if (value.startsWith("+")) return today.plusDays(Integer.parseInt(value.substring(1)));
                return LocalDate.parse(value);
            } catch (DateTimeParseException | NumberFormatException e) {
                println("  Use a date like " + today.plusDays(1) + ", or: today, tomorrow, +3");
            }
        }
    }

    public LocalDateTime askDateTime(String prompt) {
        while (true) {
            try {
                return LocalDateTime.parse(ask(prompt), DATE_TIME);
            } catch (DateTimeParseException e) {
                println("  Use the format yyyy-MM-dd HH:mm, e.g. 2026-12-01 09:30");
            }
        }
    }

    public BigDecimal askMoney(String prompt) {
        while (true) {
            try {
                BigDecimal amount = new BigDecimal(ask(prompt).replace(",", ""));
                if (amount.signum() > 0 && amount.scale() <= 2) return amount;
            } catch (NumberFormatException ignored) {
                // fall through to the hint
            }
            println("  Enter an amount like 4500 or 4500.50");
        }
    }

    /** Hidden typing when a real terminal is available. Returns char[] so it can be wiped after use. */
    public char[] askPassword(String prompt) {
        if (console != null) {
            char[] password = console.readPassword(prompt);
            if (password == null) throw new EndOfInputException();
            return password;
        }
        return ask(prompt).toCharArray();
    }

    public boolean confirm(String prompt) {
        while (true) {
            String value = ask(prompt + " [y/n]: ").toLowerCase();
            if (value.equals("y") || value.equals("yes")) return true;
            if (value.equals("n") || value.equals("no")) return false;
            println("  Please answer y or n.");
        }
    }
}
