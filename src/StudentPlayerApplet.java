import javax.sound.sampled.*;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

public class StudentPlayerApplet extends Applet
{
    private static final long serialVersionUID = 1L;
    public void init() {
        setLayout(new BorderLayout());
        add(BorderLayout.CENTER, new Player(getParameter("file")));
    }
}

class Player extends Panel implements Runnable {
    private static final long serialVersionUID = 1L;
    private TextField textfield;
    private TextArea textarea;
    private Font font;
    private String filename;
    byte[][] audioBuffer;
    AudioInputStream s;
    int bytesRead;
    AudioFormat format;
    DataLine.Info info;
    boolean ready = false;

    public Player(String filename){

        font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        textfield = new TextField();
        textarea = new TextArea();
        textarea.setFont(font);
        textfield.setFont(font);
        setLayout(new BorderLayout());
        add(BorderLayout.SOUTH, textfield);
        add(BorderLayout.CENTER, textarea);

        textfield.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(e.getActionCommand().toString().equals("x"))
                {
                    textarea.append("Shutting Down..");
                    textfield.setText("");
                    //TODO GRACEFULLY SHUT DOWN PROGRAMME
                }
                if(e.getActionCommand().toString().equals("p"))
                {
                    textarea.append("Paused Audio");
                    textfield.setText("");
                    //TODO Pause Playback
                }
                else if(e.getActionCommand().toString().equals("r"))
                {
                    textarea.append("Resumed Audio");
                    textfield.setText("");
                    //TODO Resume Playback
                }
                else if(e.getActionCommand().toString().equals("q"))
                {
                    textarea.append("Raised Volume");
                    textfield.setText("");
                    //TODO Raise Volume
                }
                else if(e.getActionCommand().toString().equals("a"))
                {
                    textarea.append("Lowered Volume");
                    textfield.setText("");
                    //TODO Lower Volume
                }
                else if(e.getActionCommand().toString().equals("m"))
                {
                    textarea.append("Muted Audio");
                    textfield.setText("");
                    //TODO Mute Playback
                }
                else if(e.getActionCommand().toString().equals("u"))
                {
                    textarea.append("Unmuted Audio");
                    textfield.setText("");
                    //TODO Unmute Playback
                }
            }
        });

        this.filename = filename;
        new Thread(this).start();
    }

    public void run() {

        try {
            s = AudioSystem.getAudioInputStream(new File(filename));
            format = s.getFormat();
            System.out.println("Audio format: " + format.toString());

            info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                throw new UnsupportedAudioFileException();
            }

            int oneSecond = (int) (format.getChannels() * format.getSampleRate() *
                    format.getSampleSizeInBits() / 8);
            audioBuffer = new byte[10][oneSecond];

            Thread producer = new Thread(new Producer());
            Thread consumer = new Thread(new Consumer());
            producer.start();
            consumer.start();
            producer.join();
            consumer.join();
            System.out.println("Finished Playing");

        } catch (UnsupportedAudioFileException e ) {
            System.out.println("Player initialisation failed");
            e.printStackTrace();
            System.exit(1);
        }  catch (IOException e) {
            System.out.println("Player initialisation failed");
            e.printStackTrace();
            System.exit(1);
        }
        catch (InterruptedException e)
        {
            System.out.println("Thread Interupted Exception");
            e.printStackTrace();
            System.exit(1);
        }
    }

    synchronized public void readAudio()
    {
        try
        {
            while(true)
            {
                while(ready)
                    wait();

                bytesRead = s.read(audioBuffer[0]);
                if(bytesRead == -1) {
                    break;
                }
                ready = true;
                notifyAll();
            }
            ready = true;
            notifyAll();
        }

        catch (InterruptedException e)
        {
            System.out.println("Thread Interupted Exception");
            e.printStackTrace();
            System.exit(1);
        }

        catch (Exception e)
        {

        }
    }

    synchronized public void writeAudio()
    {
        try
        {
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            while(true)
            {
                while (!ready)
                    wait();

                if(bytesRead == -1)
                    break;
                line.write(audioBuffer[0], 0, bytesRead);

                ready = false;
                notifyAll();

            }

            line.drain();
            line.stop();
            line.close();
        }

        catch (LineUnavailableException e) {
            System.out.println("Player initialisation failed");
            e.printStackTrace();
            System.exit(1);
        }
        catch (InterruptedException e)
        {
            System.out.println("Thread Interupted Exception");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private class Producer extends Thread
    {
        synchronized public void run()
        {
           readAudio();
        }
    }

    private class Consumer extends Thread
    {
        synchronized public void run()
        {
           writeAudio();
        }
    }
}






