package com.kuxhausen.huemore.state;

public class BulbState {
	/**
	 * brightness between 0-254 (NB 0 is not off!)
	 */
	public Integer bri;
	/**
	 * hs mode: the hue (expressed in ~deg*182)
	 */
	public Integer hue;
	/**
	 * hs mode: saturation between 0-254
	 */
	public Short sat;
	/**
	 * xy mode: CIE 1931 colour co-ordinates
	 */
	public Double[] xy;
	/**
	 * ct mode: colour temp (expressed in mireds range 154-500)
	 */
	public Integer ct;

	/**
	 * true if the light is on, false if off
	 */
	public boolean on;
	
	/**
	 * The dynamic effect of the light, can either be "none" or "colorloop"
	 */
	public String effect;

	public BulbState() {
	}
}