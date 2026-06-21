package xyz.nextalone.nagram.utils.voicechanger;

public final class HannWindow {
    private final int fftSize;
    private final int N;
    private final boolean sym;
    private final boolean norm;

    public HannWindow(int fftSize, boolean sym) {
        this(fftSize, fftSize, sym, false);
    }

    public HannWindow(int fftSize, int N, boolean sym, boolean norm) {
        this.fftSize = fftSize;
        this.N = N;
        this.sym = sym;
        this.norm = norm;
    }

    private void normalize(float[] fArr) {
        float sumSq = 0.0f;
        for (int i = 0; i < this.fftSize; i++) {
            float val = fArr[i];
            sumSq += val * val;
        }
        float scale = 1.0f / (float) Math.sqrt(sumSq / this.N);
        for (int i = 0; i < this.fftSize; i++) {
            fArr[i] *= scale;
        }
    }

    public float[] a() {
        float[] fArr = new float[this.fftSize];
        int M = this.sym ? this.fftSize + 1 : this.fftSize;
        for (int i = 0; i < this.fftSize; i++) {
            fArr[i] = (1.0f - (float) Math.cos((i * 2.0 * Math.PI) / (M - 1.0f))) * 0.5f;
        }
        if (this.norm) {
            normalize(fArr);
        }
        return fArr;
    }
}
