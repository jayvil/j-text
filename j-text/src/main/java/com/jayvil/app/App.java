package com.jayvil.app;

import java.io.IOException;
import java.util.Arrays;

import com.jayvil.app.LibC.Termios;
import com.jayvil.app.LibC.WinSize;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Structure;

public class App 
{
    private static LibC.Termios originalAttributes;
    private static int rows = 10;
    private static int cols = 10;

    public static void main( String[] args ) throws IOException {
        enableRawMode();
        initEditor();
        while(true) {
            refreshScreen();
            int key = System.in.read();
            handleKeyPress(key);
            //System.out.print((char) key + " (" + key + ")\r\n");
        }
    }

    public static void enableRawMode() {
        LibC.Termios termios = new Termios();
        int returnCode = LibC.INSTANCE.tcgetattr(LibC.SYSTEM_OUT_FD, termios);
        if (returnCode != 0) {
            System.err.println("Error calling tcgetattr");
            System.exit(returnCode);    
        }
        originalAttributes = LibC.Termios.clone(termios);
        termios.c_iflag &= ~(LibC.BRKINT | LibC.ICRNL | LibC.INPCK | LibC.ISTRIP | LibC.IXON);
        termios.c_oflag &= ~(LibC.OPOST);
        termios.c_cflag |= (LibC.CS8);
        termios.c_lflag &= ~(LibC.ECHO | LibC.ICANON |LibC.IEXTEN | LibC.ISIG);
        
        termios.c_cc[LibC.VMIN] = 0;
        termios.c_cc[LibC.VTIME] = 1;

        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, termios);
        System.out.println("termios = " + termios);
    }

    private static void refreshScreen() {
        StringBuilder builder = new StringBuilder();
        // Erase screen
        builder.append("\033[2J");
        // Reposition mouse in left corner
        builder.append("\033[H");
        for(int i = 0; i < rows-1; i++) {
            builder.append("~\r\n");
        }
        // Status bar
        String statusMessage = "J-Text - v0.0.1";
        builder.append("\033[7m")
            .append(statusMessage)
            .append(" ".repeat(Math.max(0, cols - statusMessage.length())))
            .append("\033[0m");
        builder.append("\033[H");
        System.out.print(builder);
    }

    private static LibC.WinSize getWinSize() {
        final LibC.WinSize winSize = new WinSize();
        final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.TIOCGWINSZ, winSize);
        if (rc != 0) {
            System.out.println("ioctl issue");
            System.exit(rc);
        }
        return winSize;
    }

    private static void initEditor() {
        LibC.WinSize winSize = getWinSize();
        cols = winSize.ws_col;
        rows = winSize.ws_row; 
        System.out.print("Num rows = " + rows);
        System.out.print("Num cols = " + cols); 
    }

    private static void handleKeyPress(int key) {
        if(key == 'q') {
            // Erase screen
            System.out.print("\033[2J");
            // Reposition mouse in left corner
            System.out.print("\033[H");
            LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, originalAttributes);
            System.exit(0);
        }
    } 
}


// JNA generates a proxy for the interface at runtime
interface LibC extends Library {

    int SYSTEM_OUT_FD = 1;

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
