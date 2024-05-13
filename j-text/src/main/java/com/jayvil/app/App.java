package com.jayvil.app;

import java.util.Arrays;

import com.jayvil.app.LibC.Termios;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Structure;

public class App 
{

//    public static void EnableRawMode(Termios t) {

  //  }

    public static void main( String[] args ) {
        LibC.Termios termios = new Termios();
        int returnCode = LibC.INSTANCE.tcgetattr(LibC.SYSTEM_OUT_FD, termios);

        if (returnCode != 0) {
            System.err.println("Error calling tcgetattr");
            System.exit(returnCode);    
        }

        System.out.println("termios = " + termios);
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

        public static Termios copy(Termios termios) {
            Termios copy = new Termios();
            copy.c_iflag = termios.c_iflag;
            copy.c_oflag = termios.c_oflag;
            copy.c_cflag = termios.c_cflag;
            copy.c_lflag = termios.c_lflag;
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

    public int tcgetattr(int fd, Termios termios);
    public int tcsetattr(int fd, int optional_actions, Termios termios);

}
