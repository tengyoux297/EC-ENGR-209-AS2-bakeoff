import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	String[] classNames = {"do - c", "re - d", "mi - e", "fa - f", "so - g", "la - a", "ti - b"};
	int classIndex = 0;
	int dataCount = 0;

	MLClassifier classifier;
	
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
			String guessedLabel = classifier.classify(captureInstance(null));
			
			System.out.print("Being classified here"); 
			// Yang: add code to stabilize your classification results
			
			text("classified as: " + guessedLabel, 20, 30);
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

