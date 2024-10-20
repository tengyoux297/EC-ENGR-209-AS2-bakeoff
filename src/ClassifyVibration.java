import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sound.midi.*;

import processing.core.PApplet;
import processing.sound.AudioIn;
import processing.sound.FFT;
import processing.sound.Sound;
import processing.sound.Waveform;

// save traningData
import java.io.Serializable;

/* A class with the main function and Processing visualizations to run the demo */

public class ClassifyVibration extends PApplet {

	FFT fft;
	AudioIn in;
	Waveform waveform;
	int bands = 512;

	float windowLengthMs = 1000;  // Window length in milliseconds, change the value to adjust the window length
	float fs = 44100;           // Sampling rate (samples per second), typically 44,100 Hz as the typical setting in AudioIn library
	int nsamples = (int)(fs * windowLengthMs / 1000);  // Convert ms to seconds

	// int nsamples = 1024;
	float[] spectrum = new float[bands];
	float[] fftFeatures = new float[bands];
	String[] classNames = {"quiet","do - c", "re - d", "mi - e", "fa - f", "so - g", "la - a", "ti - b"};
	int classIndex = 0;
	int dataCount = 0;

	MLClassifier classifier;
	// New variable to store the recent classifications
	List<String> recentClassifications = new ArrayList<>();
	int historySize = 10; // How many classifications to store for voting

	// Method to get the majority vote from the recent classifications
	public String getMajorityVote(List<String> classifications) {
		Map<String, Integer> counts = new HashMap<>();
		for (String label : classifications) {
			counts.put(label, counts.getOrDefault(label, 0) + 1);
		}

		// Find the label with the highest count
		String majorityLabel = null;
		int maxCount = 0;
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (entry.getValue() > maxCount) {
				majorityLabel = entry.getKey();
				maxCount = entry.getValue();
			}
		}
		return majorityLabel;
	}

	Map<String, List<DataInstance>> trainingData = new HashMap<>();
	{for (String className : classNames){
		trainingData.put(className, new ArrayList<DataInstance>());
	}}
	
	DataInstance captureInstance (String label){
		DataInstance res = new DataInstance();
		res.label = label;
		res.measurements = fftFeatures.clone();
		return res;
	}
	
	public static void main(String[] args) {
		PApplet.main("ClassifyVibration");
	}
	
	public void settings() {
		size(512, 400);
	}

	public void setup() {
		
		/* list all audio devices */
		Sound.list();
		Sound s = new Sound(this);
		  
		/* select microphone device */
		s.inputDevice(2);
		    
		/* create an Input stream which is routed into the FFT analyzer */
		fft = new FFT(this, bands);
		in = new AudioIn(this, 0);
		waveform = new Waveform(this, nsamples);
		waveform.input(in);
		
		/* start the Audio Input */
		in.start();
		  
		/* patch the AudioIn */
		fft.input(in);
	}

	public void draw() {
		background(0);
		fill(0);
		stroke(255);
		
		waveform.analyze();

		beginShape();
		  
		for(int i = 0; i < nsamples; i++)
		{
			vertex(
					map(i, 0, nsamples, 0, width),
					map(waveform.data[i], -1, 1, 0, height)
					);
		}
		
		endShape();

		fft.analyze(spectrum);

		for(int i = 0; i < bands; i++){

			/* the result of the FFT is normalized */
			/* draw the line for frequency band i scaling it up by 40 to get more amplitude */
			line( i, height, i, height - spectrum[i]*height*40);
			fftFeatures[i] = spectrum[i];
		} 

		fill(255);
		textSize(30);
		if(classifier != null) {
			// String guessedLabel = classifier.classify(captureInstance(null));	
			// // Yang: add code to stabilize your classification result
			// text("classified as: " + guessedLabel, 20, 30);
			String guessedLabel = "Unknown";
			guessedLabel = classifier.classify(captureInstance(null));

            // Add the classification result to the recent classifications
            recentClassifications.add(guessedLabel);
            if (recentClassifications.size() > historySize) {
                recentClassifications.remove(0); // Keep only the most recent `historySize` predictions
            }

            // Get the stabilized label using majority voting
            String stabilizedLabel = getMajorityVote(recentClassifications);

            text("classified as: " + stabilizedLabel, 20, 30);

            //add some code to make the UI more visual
			// C4 (middle C) = 60 D4=62 E4=64 F4=65 G4=67 A4=69 B=71 C5 = 72
			try {
				// Get a synthesizer (for output only) and open it
				Synthesizer synthesizer = MidiSystem.getSynthesizer();
				synthesizer.open();
				// Get MIDI channels (output channels for playing notes)
				MidiChannel[] channels = synthesizer.getChannels();
				MidiChannel piano = channels[0]; // Channel 0 is a piano
	
				if(guessedLabel == "do - c"){
					piano.noteOn(60, 80);  // Note 60 is Middle C, velocity 80
				}
				else if(guessedLabel == "re - d"){
					piano.noteOn(62, 80);  // Note 62 is Middle D, velocity 80
				}
				else if(guessedLabel == "mi - e"){
					piano.noteOn(64, 80);  // Note 64 is Middle E, velocity 80
				}
				else if(guessedLabel == "fa - f"){
					piano.noteOn(65, 80);  // Note 65 is Middle F, velocity 80
				}
				else if(guessedLabel == "so - g"){
					piano.noteOn(67, 80);  // Note 67 is Middle G, velocity 80	
				}
				else if(guessedLabel == "la - a"){
					piano.noteOn(69, 80);  // Note 69 is Middle A, velocity 80
				}
				else if(guessedLabel == "re - d"){
					piano.noteOn(71, 80);  // Note 71 is Middle B, velocity 80
				}		
				// Close the synthesizer when done
				Thread.sleep(1000);    // Play the note for 1 second
				piano.noteOff(60);     // Turn off the note
				synthesizer.close();

			} catch (MidiUnavailableException e) {
				System.err.println("MIDI device is unavailable: " + e.getMessage());
			} catch (InterruptedException e) {
				System.err.println("Thread was interrupted: " + e.getMessage());
			}
		}else {
			text(classNames[classIndex], 20, 30);
			dataCount = trainingData.get(classNames[classIndex]).size();
			text("Data collected: " + dataCount, 20, 60);
		}
	}

	/*
	 * This function saves the trainingData
	 */
	public void saveTrainingData(String filepath) {
        try {
            FileOutputStream fileOut = new FileOutputStream(filepath);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(trainingData);  // Serialize the trainingData list
            out.close();
            fileOut.close();
            System.out.println("Training data saved to " + filepath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	/*
	 * This function loads the trainingData
	 */
    public void loadTrainingData(String filepath) {
        try {
            FileInputStream fileIn = new FileInputStream(filepath);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            trainingData = (Map<String, List<DataInstance>>) in.readObject();  // Deserialize the trainingData map
            in.close();
            fileIn.close();
            System.out.println("Training data loaded from " + filepath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	
	public void keyPressed() {
		

		if (key == CODED && keyCode == DOWN) {
			classIndex = (classIndex + 1) % classNames.length;
		}
		
		else if (key == 't') {
			if(classifier == null) {
				println("Start training ...");
				classifier = new MLClassifier();
				classifier.train(trainingData);
			}else {
				classifier.train(trainingData); 
				//classifier = null;
			}
		}

		// save the collected trainingData to a file
		else if (key == 's') {
			saveTrainingData("trainingData.ser");
		}

		// only load
		else if (key == 'l') {
			loadTrainingData("trainingData.ser");
			classifier = new MLClassifier();
			classifier.train(trainingData);
			System.out.println("Finish loading the classifier!");
		}

		// delete classifier and the trainingData
		else if (key == 'd'){
			classifier = null;
			trainingData = new HashMap<>();
			{for (String className : classNames){
				trainingData.put(className, new ArrayList<DataInstance>());
			}}
			System.out.println("Deleted all data for class: " + classNames[classIndex]);

	    }	

		// only delete the classifier so we can add more trainingData
		else if (key == 'a'){
			classifier = null;
		}

		// otherwise we collect more data
		else {
			trainingData.get(classNames[classIndex]).add(captureInstance(classNames[classIndex]));
		}
	}

}

