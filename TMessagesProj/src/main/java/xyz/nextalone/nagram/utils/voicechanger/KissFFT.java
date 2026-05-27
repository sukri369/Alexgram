package xyz.nextalone.nagram.utils.voicechanger;

import org.telegram.messenger.FourierTransform;

public final class KissFFT {
    private final FourierTransform.FFT fft;

    public KissFFT(int size) {
        this.fft = new FourierTransform.FFT(size, 48000.0f);
    }

    public void a(float[] fArr) {
        int N = fArr.length;
        float[] temp = fArr.clone();
        fft.forward(temp);
        float[] real = fft.getSpectrumReal();
        float[] imag = fft.getSpectrumImaginary();
        for (int k = 0; k < N / 2; k++) {
            fArr[2 * k] = real[k];
            fArr[2 * k + 1] = imag[k];
        }
    }

    public void b(float[] fArr) {
        int N = fArr.length;
        float[] real = new float[N];
        float[] imag = new float[N];
        for (int k = 0; k < N / 2; k++) {
            real[k] = fArr[2 * k];
            imag[k] = fArr[2 * k + 1];
            if (k > 0) {
                real[N - k] = real[k];
                imag[N - k] = -imag[k];
            }
        }
        fft.inverse(real, imag, fArr);
    }
}
