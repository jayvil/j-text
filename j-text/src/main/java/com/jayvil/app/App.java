package com.jayvil.app;

import com.jayvil.app.LibC.Termios;
import com.sun.jna.Structure;

public class App 
{

    public static void EnableRawMode(Termios t) {
        
    }

    public static void main( String[] args )
    {
        //while() {

        //}
    }
}

interface LibC extends Library {
    int ECHO = 10;

    // Tell JNA what part of memory block belong to which field
    @Structure.FieldOrder(value = {"c_iflag", "c_oflag", "c_cflag", "c_lflag;"})
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
    }

    public int tcgetattr(int fd, Termios termios);
    public int tcsetattr(int fd, int optional_actions, Termios termios);
    
}
