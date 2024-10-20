import java.io.File;
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
	int bands = 128;

	float windowLengthMs = 500;  // Window length in milliseconds, change the value to adjust the window length
	float fs = 44100;           // Sampling rate (samples per second), typically 44,100 Hz as the typical setting in AudioIn library
	int nsamples = (int)(fs * windowLengthMs / 1000);  // Convert ms to seconds

	// int nsamples = 1024;
	float[] spectrum = new float[bands];
	float[] fftFeatures = new float[bands];
	String[] classNames = {"quiet", "do - c", "re - d", "mi - e", "fa - f", "so - g", "la - a", "ti - b"};
	int classIndex = 0;
	int dataCount = 0;

	double magnitudeThreshold = 0.05; // Adjust this value as needed

	MLClassifier classifier;


	Map<String, List<DataInstance>> trainingData = new HashMap<>();
	{for (String className : classNames){
		trainingData.put(className, new ArrayList<DataInstance>());
	}}
	
	DataInstance captureInstance (String label){
		DataInstance res = new DataInstance();
		res.label = label;
		res.measurements = fftFeatures.clone();
		normalizeDataInstance(res);
		return res;
	}
	// Z-score normalization for a single DataInstance's measurements
	public void normalizeDataInstance(DataInstance instance) {
		// Calculate mean and standard deviation for the instance's measurements
		float mean = 0;
		float sumSquaredDiffs = 0;

		for (float measurement : instance.measurements) {
			mean += measurement;
		}
		mean /= instance.measurements.length;

		for (float measurement : instance.measurements) {
			sumSquaredDiffs += (measurement - mean) * (measurement - mean);
		}
		float stddev = (float) Math.sqrt(sumSquaredDiffs / instance.measurements.length);

		// Apply Z-score normalization
		for (int i = 0; i < instance.measurements.length; i++) {
			if (stddev != 0) {
				instance.measurements[i] = (instance.measurements[i] - mean) / stddev;
			} else {
				instance.measurements[i] = 0;  // If stddev is 0, set normalized value to 0
			}
		}
	}
	// Z-score normalization for fftFeatures array
	public void normalizeFftFeatures() {
		float mean = 0;
		float sumSquaredDiffs = 0;

		// Calculate mean
		for (float feature : fftFeatures) {
			mean += feature;
		}
		mean /= fftFeatures.length;

		// Calculate standard deviation
		for (float feature : fftFeatures) {
			sumSquaredDiffs += (feature - mean) * (feature - mean);
		}
		float stddev = (float) Math.sqrt(sumSquaredDiffs / fftFeatures.length);

		// Apply Z-score normalization
		for (int i = 0; i < fftFeatures.length; i++) {
			if (stddev != 0) {
				fftFeatures[i] = (fftFeatures[i] - mean) / stddev;
			} else {
				fftFeatures[i] = 0;  // If stddev is 0, set normalized value to 0
			}
		}
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
		
		if (classifier == null) {
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

		// for(int i = 0; i < bands; i++){
		// 	if (spectrum[i] >= magnitudeThreshold) {
		// 		// Only consider magnitudes above the threshold
		// 		line(i, height, i, height - spectrum[i] * height * 40);
		// 		fftFeatures[i] = spectrum[i];
		// 	} else {
		// 		fftFeatures[i] = 0; // Ignore or reset low magnitudes
		// 	}
		// }
		}
		// Display the classification result
		fill(255);
		textSize(30);

		if(classifier != null) {
			normalizeFftFeatures();
			drawStaff();
        	
			String guessedLabel = classifier.classify(captureInstance(null));	
			// Yang: add code to stabilize your classification result
			text("classified as: " + guessedLabel, 20, 30);
			drawNoteOnStaff(guessedLabel);
            //add some code to make the UI more visual
			// C4 (middle C) = 60 D4=62 E4=64 F4=65 G4=67 A4=69 B=71 C5 = 72
			try {
				if(!guessedLabel.equals("quiet")){
					// Get a synthesizer (for output only) and open it
					Synthesizer synthesizer = MidiSystem.getSynthesizer();
					synthesizer.open();
					// Get MIDI channels (output channels for playing notes)
					MidiChannel[] channels = synthesizer.getChannels();
					MidiChannel piano = channels[0]; // Channel 0 is a piano
		
					if(guessedLabel.equals("do - c")){
						piano.noteOn(60, 80);  // Note 60 is Middle C, velocity 80
					}
					else if(guessedLabel.equals("re - d")){
						piano.noteOn(62, 80);  // Note 62 is Middle D, velocity 80
					}
					else if(guessedLabel.equals("mi - e")){
						piano.noteOn(64, 80);  // Note 64 is Middle E, velocity 80
					}
					else if(guessedLabel.equals("fa - f")){
						piano.noteOn(65, 80);  // Note 65 is Middle F, velocity 80
					}
					else if(guessedLabel.equals("so - g")){
						piano.noteOn(67, 80);  // Note 67 is Middle G, velocity 80	
					}
					else if(guessedLabel.equals("la - a")){
						piano.noteOn(69, 80);  // Note 69 is Middle A, velocity 80
					}
					else if(guessedLabel.equals("ti - b")){
						piano.noteOn(71, 80);  // Note 71 is Middle B, velocity 80
					}		
					// Close the synthesizer when done
					Thread.sleep(1000);    // Play the note for 1 second
					piano.noteOff(60);     // Turn off the note
					synthesizer.close();
				}


			} catch (MidiUnavailableException e) {
				System.err.println("MIDI device is unavailable: " + e.getMessage());
			} catch (InterruptedException e) {
				System.err.println("Thread was interrupted: " + e.getMessage());
			}
		}
			fill(255);
		textSize(30);
		
	
		if (classifier != null) {
			// Classification
			String guessedLabel = classifier.classify(captureInstance(null));
	
	
			text("classified as: " + guessedLabel, 20, 30);
	
			// Draw the musical staff and the recognized note as a square on the staff
			drawStaff();
			drawNoteOnStaff(guessedLabel);
	
			// MIDI code follows as before...
		} else {
			// If classifier is not initialized, display class index and data count
			text(classNames[classIndex], 20, 30);
			dataCount = trainingData.get(classNames[classIndex]).size();
			text("Data collected: " + dataCount, 20, 60);
		}
	}
int lineSpacing = 20;
	// Centered and enlarged staff
void drawStaff() {
    int staffHeight = 5 * lineSpacing;  // Total height of the 5-line staff
    int staffTop = height / 2 - staffHeight / 2;  // Center the staff vertically

    // Draw the 5 horizontal lines of the staff
    for (int i = 0; i < 5; i++) {
        line(50, staffTop + i * lineSpacing, width - 50, staffTop + i * lineSpacing);
    }
}

// Function to map notes to their position on the staff
void drawNoteOnStaff(String note) {
    int staffHeight = 5 * lineSpacing;
    int staffTop = height / 2 - staffHeight / 2;
    int noteX = 0;       // X-position for the note (you can change or animate this)

    // Map note names to Y positions on the staff
    int noteY = 0;
    switch (note) {
        case "do - c":
			noteX = 100;
            noteY = staffTop + 4 * lineSpacing;  // Middle C on the first ledger line below the staff
            break;
        case "re - d":
			noteX = 150;
            noteY = staffTop + (int)(3.5 * lineSpacing);  // D in the space below the staff
            break;
        case "mi - e":
			noteX = 200;
            noteY = staffTop + (int)(3 * lineSpacing);  // E on the first line of the staff
            break;
        case "fa - f":
			noteX = 250;
            noteY = staffTop + (int)(2.5 * lineSpacing);  // F in the first space of the staff
            break;
        case "so - g":
			noteX = 300;
            noteY = staffTop + 2 * lineSpacing;  // G on the second line of the staff
            break;
        case "la - a":
			noteX = 350;
            noteY = staffTop + (int)(1.5 * lineSpacing);  // A in the second space of the staff
            break;
        case "ti - b":
			noteX = 400;
            noteY = staffTop + 1 * lineSpacing;  // B on the third line of the staff
            break;
        case "quiet":
            return; // Do not draw anything if the input is quiet
    }

    // Draw the note as a small square or circle on the staff
    fill(255, 0, 0);  // Red color for the note
    noStroke();
    rect(noteX, noteY - 10, 20, 20);  // Draw the square note
}

	/*
	 * This function saves the trainingData
	 */
	public void saveTrainingData(String filepath) {
		File file = new File(filepath);

		// delete the existing file
		if(file.exists()){
			if(!file.delete()){
				System.out.println("Failed to delete the file: " + filepath);
			}
		}
		
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
			 // Normalize the loaded training data
			 for (String className : trainingData.keySet()) {
				for (DataInstance instance : trainingData.get(className)) {
					normalizeDataInstance(instance);
				}
			}
            System.out.println("Training data loaded and normalized from " + filepath);
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

