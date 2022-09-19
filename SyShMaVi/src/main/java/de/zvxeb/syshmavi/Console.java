package de.zvxeb.syshmavi;

import de.zvxeb.jkeyboard.KeyReleaseListener;

import java.awt.event.KeyEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.function.Consumer;

public class Console implements KeyReleaseListener {

    public static final int BACK_SPACE = KeyEvent.VK_BACK_SPACE;
    public static final int DELETE = KeyEvent.VK_DELETE;
    public static final int ENTER = KeyEvent.VK_ENTER;
    public static final int LEFT = KeyEvent.VK_LEFT;
    public static final int RIGHT = KeyEvent.VK_RIGHT;
    public static final int UP = KeyEvent.VK_UP;
    public static final int DOWN = KeyEvent.VK_DOWN;
    public static final int ESCAPE = KeyEvent.VK_ESCAPE;
    public static final int HOME = KeyEvent.VK_HOME;
    public static final int END = KeyEvent.VK_END;

    private boolean active = false;

    private String currentLine = "";
    private int currentLineIndex = 0;

    private int currentLogEntry = 0;

    private ArrayList<String> log = new ArrayList<>();

    private Consumer<String> commandConsumer;

    public Console() {
        addToLog(Instant.now().toString());
    }

    public boolean isActive() {
        return active;
    }

    public Console setActive(boolean active) {
        this.active = active;
        return this;
    }

    public Consumer<String> getCommandConsumer() {
        return commandConsumer;
    }

    public Console setCommandConsumer(Consumer<String> commandConsumer) {
        this.commandConsumer = commandConsumer;
        return this;
    }

    @Override
    public void keyReleased(int keyid, char keychar) {
        if(isActive()) {
            if(validKeyChar(keyid, keychar)) {
                switch (keyid) {
                    case ENTER:
                        execute();
                        break;
                    case BACK_SPACE:
                        remove_char(true);
                        break;
                    case DELETE:
                        remove_char(false);
                        break;
                    case ESCAPE:
                        clear();
                        break;
                    case LEFT:
                        left();
                        break;
                    case RIGHT:
                        right();
                        break;
                    case UP:
                    case DOWN:
                        break;
                    case HOME:
                        home();
                        break;
                    case END:
                        end();
                        break;
                    default:
                        add_char(keychar);
                }
            }
        }
    }

    private void left() {
        if(currentLineIndex > 0) {
            currentLineIndex--;
        }
    }

    private void right() {
        if(currentLineIndex < currentLine.length()) {
            currentLineIndex++;
        }
    }

    private void execute() {
        System.out.format("Execute: %s\n", currentLine);
        String command = currentLine;
        currentLine = "";
        currentLineIndex = 0;
        if(commandConsumer!=null) {
            commandConsumer.accept(command);
        }
    }

    private void clear() {
        currentLine = "";
        currentLineIndex = 0;
    }

    private void remove_char(boolean before) {
        // backspace ?
        if(before) {
            if (currentLineIndex > 0) {
                if (currentLineIndex == currentLine.length()) {
                    currentLine = currentLine.substring(0, currentLineIndex - 1);
                } else {
                    if (currentLineIndex == 1) {
                        currentLine = currentLine.substring(1);
                    } else {
                        currentLine = currentLine.substring(0, currentLineIndex - 1) + currentLine.substring(currentLineIndex);
                    }
                }
                currentLineIndex--;
            }
        } else { // delete
            if(currentLineIndex < currentLine.length()) {
                if(currentLineIndex == 0) {
                    currentLine = currentLine.substring(1);
                } else {
                    currentLine = currentLine.substring(0, currentLineIndex) + currentLine.substring(currentLineIndex+1);
                }
            }
        }
    }

    private void home() {
        currentLineIndex = 0;
    }

    private void end() {
        currentLineIndex = currentLine.length();
    }

    private void add_char(char keychar) {
        if(keychar < 0x20 || keychar >= 0x80) return;

        char c = keychar;
        if(currentLineIndex == currentLine.length()) {
            currentLine += c;
        } else {
            currentLine = currentLine.substring(0, currentLineIndex) + c + currentLine.substring(currentLineIndex);
        }
        currentLineIndex++;
    }


    private boolean validKeyChar(int keyid, char keychar) {
        if(keyid == BACK_SPACE) return true;
        if(keyid == DELETE) return true;
        if(keyid == ENTER) return true;
        if(keyid == LEFT) return true;
        if(keyid == RIGHT) return true;
        if(keyid == UP) return true;
        if(keyid == DOWN) return true;
        if(keyid == ESCAPE) return true;
        if(keyid == HOME) return true;
        if(keyid == END) return true;

        return keychar >= 0x20 && keychar < 0x80;
    }

    public String currentLine() {
        return currentLine;
    }

    public int cursorIndex() {
        return currentLineIndex;
    }

    public int logEntries() {
        return log.size();
    }

    public void addToLog(String line) {
        log.add(line);
        currentLogEntry = log.size()-1;
    }

    public int currentLogEntry() {
        return currentLogEntry;
    }

    public String getLogEntry(int index) {
        if(index < 0) return "";
        if(index >= log.size()) return "";
        return log.get(index);
    }
}
