package com.jayvil.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jayvil.app.LibC.Termios;
import com.jayvil.app.LibC.WinSize;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Structure;

public class App 
{
    private static LibC.Termios originalAttributes;
    // Default rows and cols
    private static int rows = 10, cols = 10;
    private static final int ARROW_UP = 1000,
                            ARROW_DOWN = 1001,
                            ARROW_LEFT = 1002,
                            ARROW_RIGHT = 1003,
                            HOME = 1004, END = 1005, PAGEUP = 1006,
                            PAGEDOWN = 1007,
                            DEL = 1008;
    // Used to keep track of the size of the window
    private static int cursorXPos = 0, cursorYPos = 0;
    // File content rows stored as a list
    private static List<String> content = List.of();
    // Keep track of what row of the file the user is currently scrolled to
    private static int rowOffset = 0;

    public static void main( String[] args ) throws IOException {
        openFile(args);
        enableRawMode();
        initEditor();
        while(true) {
            scroll();
            refreshScreen();
            int key = readKey();
            handleKeyPress(key);
            //System.out.print((char) key + " (" + key + ")\r\n");
        }
    }

    private static void openFile(String[] args) {
        if (args.length == 1) {
            String filename = args[0];
            //System.out.println(filename);
            Path path = Path.of(filename);
            if (Files.exists(path)) {
                try (Stream<String> stream = Files.lines(path)){
                    content = stream.collect(Collectors.toList());
                    //System.out.println(content);
                } catch (IOException e) {
                    //TODO show exception in status bar
                } 
            }
        }
    }

    public static void enableRawMode() {
        LibC.Termios termios = new Termios();
        int returnCode = LibC.INSTANCE.tcgetattr(LibC.SYSTEM_OUT_FD, termios);
        if (LibC.INSTANCE.tcgetattr(LibC.SYSTEM_OUT_FD, termios) != 0) {
            System.err.println("Error calling tcgetattr");
            System.exit(-1);    
        }
        originalAttributes = LibC.Termios.clone(termios);
        termios.c_iflag &= ~(LibC.BRKINT | LibC.ICRNL | LibC.INPCK | LibC.ISTRIP | LibC.IXON);
        termios.c_oflag &= ~(LibC.OPOST);
        termios.c_cflag |= (LibC.CS8);
        termios.c_lflag &= ~(LibC.ECHO | LibC.ICANON |LibC.IEXTEN | LibC.ISIG);
       
        // The min number of bytes of input needed before read() can return
        termios.c_cc[LibC.VMIN] = 0;
        // Sets the max amount of time to wait before read() returns
        termios.c_cc[LibC.VTIME] = 1;

        if (LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, termios) != 0) {
            System.err.println("Error calling tcsetattr");
            System.exit(-1); 
        }
        //System.out.println("termios = " + termios);
    }

    private static void disableRawMode() {
        LibC.INSTANCE.tcsetattr(LibC.STDIN_FILENO, LibC.TCSAFLUSH, originalAttributes);
    }
    private static void refreshScreen() {
        StringBuilder builder = new StringBuilder();
        eraseScreen(builder);
        moveCursorToTopLeft(builder);
        drawContent(builder);
        drawStatusBar(builder);
        drawCursor(builder);
        System.out.print(builder);
    }

    private static void eraseScreen(StringBuilder builder) {
        // Erase screen
        builder.append("\033[2J");
    }

    private static void moveCursorToTopLeft(StringBuilder builder) {
        // Reposition mouse in left corner
        builder.append("\033[H");
    }

    private static void drawContent(StringBuilder builder) {
        for(int i = 0; i < rows; i++) {
            int fileIndex = rowOffset + i;
            if (fileIndex >= content.size()) {
                builder.append("~");
            } else {
                builder.append(content.get(fileIndex));
            }
            builder.append("\033[K\r\n");
        }
    }

    private static void drawStatusBar(StringBuilder builder) {
        // Status bar
        String statusMessage = "J-Text - v0.0.1 Rows: " + rows + " X: " + cursorXPos + " Y: " + cursorYPos;
        builder.append("\033[7m")
            .append(statusMessage)
            .append(" ".repeat(Math.max(0, cols - statusMessage.length())))
            .append("\033[0m");
    }

    private static void drawCursor(StringBuilder builder) {
        // Update cursor to correct position
        builder.append(String.format("\033[%d;%dH", cursorYPos - rowOffset + 1, cursorXPos+1));
    }

    private static LibC.WinSize getWinSize() {
        final LibC.WinSize winSize = new WinSize();
        // TODO: ioctl may not work on all terminals. Update to have fallback method of getting rows, cols
        final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.TIOCGWINSZ, winSize);
        if (rc != 0 || winSize.ws_col == 0) {
            System.out.println("ioctl issue");
            System.exit(rc);
        }
        return winSize;
    }

    private static void initEditor() {
        LibC.WinSize winSize = getWinSize();
        cols = winSize.ws_col;
        // Leave room for status row at the bottom
        rows = winSize.ws_row-1;
        System.out.print("Num rows = " + rows);
        System.out.print("Num cols = " + cols); 
    }

    private static void scroll() {
        if (cursorYPos >= rows + rowOffset) {
            rowOffset = cursorYPos - rows + 1;
        }
        else if (cursorYPos < rowOffset) {
            rowOffset = cursorYPos;
        }
    }

    private static int readKey() throws IOException {
        int key = System.in.read();
        //System.out.println((char)key + " " + key);
        if (key != '\033') {
            return key;
        }
        int nextKey = System.in.read();
        if (nextKey != '[') {
            return nextKey;
        }
        int anotherKey = System.in.read();
        if (nextKey == '[') {
            return switch (anotherKey) {
                case 'A' -> ARROW_UP;
                case 'B' -> ARROW_DOWN;
                case 'C' -> ARROW_RIGHT;
                case 'D' -> ARROW_LEFT;
                case 'H' -> HOME;
                case 'F' -> END;
                case '0','1','2','3','4','5','6','7','8','9' -> { // ex esc[5~ PAGEUP
                    int nextChar = System.in.read();
                    if (nextChar != '~') {
                        yield nextChar;
                    }
                    switch (nextChar) {
                        case '1':
                        case '7':
                            yield HOME;
                        case '3':
                            yield DEL;
                        case '4':
                        case '8':
                            yield END;
                        case '5':
                            yield PAGEUP;
                        case '6':
                            yield PAGEDOWN;
                        default: yield nextChar;
                    }
                }
                default -> anotherKey;
            };
        } else {
            return switch (anotherKey) {
                case 'H' -> HOME;
                case 'F' -> END;
                default -> anotherKey;
            };
        }
        //return key;
    }
 
    private static void handleKeyPress(int key) {
        int ctrl_Q = key & 0x1f;
        if(key == ctrl_Q) {
            // Erase screen
            System.out.print("\033[2J");
            // Reposition mouse in left corner
            System.out.print("\033[H");
            //LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, originalAttributes);
            disableRawMode();
            System.exit(0);
        } 
        //else {
            //System.out.print((char) + key + "-> (" + key + ")\r\n");
        //}
        // LDUR ;)
        else if (List.of(ARROW_LEFT, ARROW_DOWN, ARROW_UP, ARROW_RIGHT).contains(key)) {
            moveCursor(key);
        }
    }

    private static void moveCursor(int key) {
        switch (key) {
            case ARROW_LEFT -> {
                if ((cursorXPos > 0)) {
                    cursorXPos--;
                }
            }
            case ARROW_DOWN -> {
                if (cursorYPos < content.size()) {
                    cursorYPos++;
                }
            }
            case ARROW_UP -> {
                if (cursorYPos > 0) {
                    cursorYPos--;
                }
            }
            case ARROW_RIGHT -> {
                if (cursorXPos < cols-1) {
                    cursorXPos += 1;
                }
            }
            case HOME -> cursorXPos = 0;
            case END -> cursorYPos = cols-1;
        } 
    }
}


// JNA generates a proxy for the interface at runtime
interface LibC extends Library {

    int SYSTEM_OUT_FD = 1;
    int STDIN_FILENO = 0;

    // c_iflag
    int IGNBRK = 1;     // Ignore break condition
    int BRKINT = 2;     // Signal interrupt on break. If IGNBRK is set, a BREAK is ignored
    int PARMRK = 10;    // Mark parity and framing errors
    int ISTRIP = 40;    // Strip 8th bit off characters
    int INLCR  = 100;   // Map NL to CR on input
    int IGNCR  = 200;   // Ignore CR (Carriage Return)
    int ICRNL  = 400;   // Map CR to NL on input
    int IXON   = 2000;  // Enable start/stop output control
    int INPCK  = 20;    // Enable input parity check

    // c_oflag
    int OPOST  = 1;     // Post process output

    // c_lflag
    int ECHO   = 10;     // Echo input characters
    int ECHONL = 100;    // If ICANON is also setm echo the NL character even if ECHO is not set
    int ICANON = 2;      // Enable canonical mode
    int ISIG   = 1;      // when any of the characters INTR, QUIT, SUSP, or DSUSP are received gereate the corresponding signal
    int IEXTEN = 100000; // Enable implementation-defined input processing

    // c_cflag
    int CSIZE  = 60;     // Character size mask. Values are CS5, CS6, CS7, CS8
    int PARENB = 400;    // Enable parity generation on output and parity checking for input
    int CS8    = 60;

    // c_cc array
    int VMIN  = 6; // Minimum number of characters for noncanonical read
    int VTIME = 5;  // Timeout in deciseconds for noncanonical read (TIME)

    int TCSAFLUSH = 2; // The change occurs after all output written to the object referred by fd has been transmitted, and all  in‐put that has been received but not read will be discarded before the change is made.
    int TIOCGWINSZ = 0x5413;

    // Use INSTANCE to query operating system
    LibC INSTANCE = Native.load(Platform.isWindows()?"msvcrt":"c", LibC.class);

    // Tell JNA what part of memory block belong to which field
    @Structure.FieldOrder(value = {"c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc"})
    class Termios extends Structure {
        /* input modes,  output modes, control modes, local modes  */     
        public int c_iflag, c_oflag, c_cflag, c_lflag; 
        /* special characters */
        public byte[] c_cc = new byte[19];

        public Termios() {}

        public static Termios clone(Termios termios) {
            Termios copy = new Termios();
            copy.c_iflag = termios.c_iflag;
            copy.c_oflag = termios.c_oflag;
            copy.c_cflag = termios.c_cflag;
            copy.c_lflag = termios.c_lflag;
            System.arraycopy(termios.c_cc, 0, copy.c_cc, 0, copy.c_cc.length);
            return copy;
        }

        @Override
        public String toString() {
            return "Termios {" +
                    " c_iflag = " + c_iflag +
                    ", c_oflag = " + c_oflag +
                    ", c_cflag = " + c_cflag +
                    ", c_lflag = " + c_lflag +
                    ", c_cc = " + Arrays.toString(c_cc) + "}";                    
        }
    }

    @Structure.FieldOrder(value = {"ws_row", "ws_col", "ws_xpixel", "ws_ypixel"})
    class WinSize extends Structure {
        public short ws_row, ws_col, ws_xpixel, ws_ypixel;
        public WinSize() {};
    }

    public int tcgetattr(int fd, Termios termios);
    public int tcsetattr(int fd, int optional_actions, Termios termios);
    public int ioctl(int fd, int request, WinSize winSize);
}
