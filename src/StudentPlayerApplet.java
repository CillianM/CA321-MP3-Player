import javax.sound.sampled.*;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

class Player extends Panel implements Runnable {
    //Applet Variables
    private static final long serialVersionUID = 1L;
    private TextField textfield;
    private TextArea textarea;
    private Font font;
    private String filename;

    //Thread Variables
    private Producer producer;
    private Consumer consumer;

    //Thread Control Variables
    private boolean ready = false;
    private boolean muted = false;

    //Bounded Buffer Variables
    private int length;
    private int oneSecond;
    private BoundedBuffer buffer;

    //Audio Read/Write Variables
    private SourceDataLine line;
    private byte[] audioBuffer;
    private AudioInputStream s;
    private int numBytesRead;
    private AudioFormat format;
    private DataLine.Info info;

    //Volume Control Variables
    private FloatControl volume;
    float volumeMin;
    float volumeMax;
    float newVolume;
    float currentVolume;

    public Player(String filename){

        font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        textfield = new TextField();
        textarea = new TextArea();
        textarea.setFont(font);
        textfield.setFont(font);
        setLayout(new BorderLayout());
        add(BorderLayout.SOUTH, textfield);
        add(BorderLayout.CENTER, textarea);

        /*
        * Java 8u11 bug requires text fields to be set as something before it's emptied
        * this requires us to use textfield.setText(" ") before textfield.setText("");
        * */

        textfield.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                if(e.getActionCommand().toString().equals("x"))
                {
                    textfield.setText(" ");
                    textfield.setText("");
                    producer.stopProducer();
                    consumer.stopConsumer();
                    buffer.stopBuffer();
                    textarea.append("Shutting Down.. \n");
                }
                if(e.getActionCommand().toString().equals("p"))
                {
                    //TODO Tidy Pause Method
                    if(!buffer.paused)
                    {
                        textfield.setText(" ");
                        textfield.setText("");

                        textarea.append("Pausing Audio \n");
                        consumer.pauseConsumer();
                    }

                }
                else if(e.getActionCommand().toString().equals("r"))
                {
                    //TODO Tidy Resume Method
                    if(buffer.paused)
                    {
                        textfield.setText(" ");
                        textfield.setText("");

                        textarea.append("Resumed Audio \n");
                        consumer.paused = false;
                        buffer.resumeBuffer();
                    }

                }
                else if(e.getActionCommand().toString().equals("q"))
                {
                    textfield.setText(" ");
                    textfield.setText("");

                    //Increment current volume level by 1
                    newVolume = volume.getValue() + 1F;

                    //Ensure we don't go over the max volume
                    if(newVolume < volumeMax) {
                        volume.setValue(newVolume);
                        textarea.append("Raised Volume \n");
                    }
                    else {
                        textarea.append("Max Volume! \n");
                    }
                }
                else if(e.getActionCommand().toString().equals("a"))
                {
                    textfield.setText(" ");
                    textfield.setText("");

                    //Decrement current volume by one
                    newVolume = volume.getValue() - 1F;

                    //Ensure we don't go under the min volume
                    if(newVolume > volumeMin) {
                        volume.setValue(newVolume);
                        textarea.append("Lowered Volume \n");
                    }
                    else {
                        textarea.append("Min Volume! \n");
                    }
                }
                else if(e.getActionCommand().toString().equals("m"))
                {
                    textfield.setText(" ");
                    textfield.setText("");

                    if(!muted) {
                        //Save whatever the current volume level is
                        currentVolume = volume.getValue();

                        //Set volume to the lowest setting ==> "Muted"
                        volume.setValue(volumeMin);
                        textarea.append("Muted Audio \n");
                        muted = true;
                    }
                    else
                    {
                        textarea.append("Already Muted! \n");
                    }
                }
                else if(e.getActionCommand().toString().equals("u"))
                {
                    textfield.setText(" ");
                    textfield.setText("");

                    if(muted)
                    {
                        //Set the volume back to the same volume it was when it was muted
                        volume.setValue(currentVolume);
                        textarea.append("Unmuted Audio \n");
                        muted = false;
                    }
                    else
                    {
                        textarea.append("Audio Not Muted! \n");
                    }
                }
            }
        });

        //Start player thread
        this.filename = filename;
        new Thread(this).start();
    }

    public void run() {

        try {
            File file = new File(filename);
            length = (int)file.length();
            s = AudioSystem.getAudioInputStream(file);
            format = s.getFormat();
            textarea.append("Audio format: " + format.toString() + "\n");

            info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                throw new UnsupportedAudioFileException();
            }


            oneSecond = (int) (format.getChannels() * format.getSampleRate() *
                    format.getSampleSizeInBits() / 8);
            buffer = new BoundedBuffer(oneSecond * 10);
            audioBuffer = new byte[oneSecond];

            //Once Audio format, readers and writers setup start the threads
            producer = new Producer();
            consumer = new Consumer();
            Thread t1 = new Thread(producer);
            Thread t2 = new Thread(consumer);
            t1.start();
            t2.start();

            t1.join();
            t2.join();
            textarea.append("Threads Joined \n");


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

    private class Producer implements Runnable
    {
        //local boolean to shut down thread
        boolean done = false;

        synchronized public void run()
        {
            //Check if it's done reading the file or ended prematurely
            while(!done && numBytesRead != -1)
            {
                try
                {
                    //Read data to audio buffer and have the length of it send to numBytesRead
                    numBytesRead = s.read(audioBuffer);
                    buffer.insertChunk(audioBuffer);
                }
                catch (Exception e) {}

                buffer.isDone();
            }
            textarea.append("Done reading from file \n");
        }

        public void stopProducer()
        {
            done = true;
        }

    }

    private class Consumer implements Runnable
    {
        //boolean to shut down thread and to pause consumer
        //=> No need to shut down producer as it can fill up the buffer while it's waiting
        boolean done = false;
        boolean paused = false;

        synchronized public void run()
        {
            try
            {
                //initialize the line
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format);
                //set up volume control
                volume = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                volumeMax = volume.getMaximum();
                volumeMin = volume.getMinimum();
                line.start();
                byte [] audioData;

                while(!done)
                {
                    try
                    {
                        //remove chunk (1 second) of data from the buffer
                        audioData = buffer.removeChunk();
                        //if it's a null it's because we've reached the end of the file or ended prematurely
                        if(audioData == null)
                            break;

                        //pause the consumer on users request
                        while(paused)
                        {
                            try
                            {
                                wait();
                            }
                            catch (InterruptedException e)
                            {}
                        }

                        //write chunk of data to device with length of it (1 second)
                        line.write(audioData, 0, audioData.length);
                    }
                    catch (Exception e)
                    {
                        System.out.println("Thread Interrupted Exception");
                        e.printStackTrace();
                        System.exit(1);
                    }
                }

                //Close down line and exit thread
                textarea.append("Done writing to device \n");
                line.drain();
                line.stop();
                line.close();
            }
            catch (LineUnavailableException e)
            {
                System.out.println("Player initialisation failed");
                e.printStackTrace();
                System.exit(1);
            }
        }

        //stop the consumer prematurely
        public void stopConsumer()
        {
            done = true;
        }

        //pause the consumer and the buffer
        public void pauseConsumer()
        {
            paused = true;
            buffer.pauseBuffer();
        }

    }

    private class BoundedBuffer
    {
        int nextIn = 0;
        int nextOut = 0;
        int size;
        int occupied = 0;
        byte [] oneSecondBlock = new byte[oneSecond];
        boolean paused;
        byte[] outs;
        boolean dataAvailable;
        byte [] bufferArray;

        BoundedBuffer(int size)
        {
            //initialize the buffer to 10 x 1 second = 10 seconds
            bufferArray = new byte[10 * oneSecond];
            textarea.append(10 * oneSecond + "\n");
            this.size = size;
            dataAvailable = true;
        }

        synchronized void insertChunk(byte [] data)
        {
            System.out.println("Inserted at " + ((nextIn + (data.length -1) % (oneSecond * 10)) + " and occupied is " + occupied));
            try {
                //if there is no spaces then wait
                while (occupied == 10) wait();

                //testing singular segmented array
                //bufferArray[nextIn] = data;
                for (int i = 0; i < data.length; i++)
                {
                    //Go byte by byte up to the 1 second boundary and fill it into the buffer
                    bufferArray[(nextIn + i) % (oneSecond * 10)] = data[i];
                }

                //increment the pointed by 1 second worth of bytes
                nextIn+= data.length % (oneSecond * 10);
                //show there is a space taken
                occupied++;
                notifyAll();
            }
            catch (InterruptedException e)
            {
                System.out.println("Thread Interrupted Exception");
                e.printStackTrace();
                System.exit(1);
            }


        }

        synchronized byte[] removeChunk()
        {
            //get a new array of bytes to send out
            outs = new byte[oneSecond];
            System.out.println("Removed at " + ((nextOut + (oneSecondBlock.length - 1)) % (oneSecond * 10)) + " and occupied is " + occupied);
            try
            {
                //if there is nothing to take and if we've ended then return a null
                if (occupied == 0 && !dataAvailable) {
                    return null;
                }

                //if there is nothing to take or we've paused then we wait
                while (occupied == 0 || paused)
                {
                    wait();
                }

                //we take byte by byte away from the buffer and add it to our new one second array
                for (int i = 0; i < oneSecondBlock.length; i++)
                {
                    outs[i] = bufferArray[(nextOut + i) % (oneSecond * 10)];
                }

                //ensuring we never go over the full ten seconds and increment a second each time
                nextOut += oneSecondBlock.length % (oneSecond * 10);
                occupied--;
                notifyAll();
                //return the now filled array
                return outs;
            }

            catch (InterruptedException e)
            {
                System.out.println("Thread Interrupted Exception");
                e.printStackTrace();
                System.exit(1);
            }
            return outs;
        }

        //pause the buffer from consuming data from the buffer
        public synchronized void pauseBuffer()
        {
            paused = true;
            notifyAll();
        }

        //allow it to continue consuming data from the buffer
        public synchronized void resumeBuffer()
        {

            paused = false;
            notifyAll();
        }

        //stop the buffer prematurely
        public synchronized void stopBuffer()
        {
            occupied = 0;
            notifyAll();
        }

        //when we've reached the end of the file we can signal the removeblock to stop
        public synchronized  void isDone()
        {
            dataAvailable = false;
        }
    }
}

public class StudentPlayerApplet extends Applet
{
    private static final long serialVersionUID = 1L;
    public void init() {
        setLayout(new BorderLayout());
        add(BorderLayout.CENTER, new Player(getParameter("file")));
    }
}

