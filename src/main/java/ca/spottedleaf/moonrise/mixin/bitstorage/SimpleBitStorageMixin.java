package ca.spottedleaf.moonrise.mixin.bitstorage;

import ca.spottedleaf.concurrentutil.util.IntegerUtil;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleBitStorage.class)
public abstract class SimpleBitStorageMixin implements BitStorage {

    @Shadow
    @Final
    private int bits;

    @Shadow
    @Final
    private long[] data;

    @Shadow
    @Final
    private int valuesPerLong;



    /*
     This is how the indices are supposed to be computed:
         final int dataIndex = index / this.valuesPerLong;
         final int localIndex = (index % this.valuesPerLong) * this.bitsPerValue;
     where valuesPerLong = 64 / this.bits
     The additional add that mojang uses is only for unsigned division, when in reality the above is signed division.
     Thus, it is appropriate to use the signed division magic values which do not use an add.
     */



    @Unique
    private static final long[] BETTER_MAGIC = new long[33];
    static {
        for (int i = 1; i < BETTER_MAGIC.length; ++i) {
            BETTER_MAGIC[i] = IntegerUtil.getDivisorNumbers(64 / i);
        }
    }

    @Unique
    private long magic;

    /**
     * @reason Init magic field
     * @author Spottedleaf
     */
    @Inject(
            method = "<init>(II[J)V",
            at = @At(
                    value = "RETURN"
            )
    )
    private void init(final CallbackInfo ci) {
        this.magic = BETTER_MAGIC[this.bits];
    }

    /**
     * @reason Optimise method to use our magic value, which does not perform an add
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public int getAndSet(final int index, final int value) {
        // assume index/value in range
        final long magic = this.magic;
        final int bits = this.bits;
        final long mul = magic >>> 32;
        final int dataIndex = (int)(((long)index * mul) >>> magic);

        final long[] dataArray = this.data;

        final long data = dataArray[dataIndex];
        final long mask = (1L << bits) - 1; // avoid extra memory read


        final int bitIndex = (index - (dataIndex * this.valuesPerLong)) * bits;
        final int prev = (int)(data >> bitIndex & mask);

        dataArray[dataIndex] = data & ~(mask << bitIndex) | ((long)value & mask) << bitIndex;

        return prev;
    }

    /**
     * @reason Optimise method to use our magic value, which does not perform an add
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public void set(final int index, final int value) {
        // assume index/value in range
        final long magic = this.magic;
        final int bits = this.bits;
        final long mul = magic >>> 32;
        final int dataIndex = (int)(((long)index * mul) >>> magic);

        final long[] dataArray = this.data;

        final long data = dataArray[dataIndex];
        final long mask = (1L << bits) - 1; // avoid extra memory read

        final int bitIndex = (index - (dataIndex * this.valuesPerLong)) * bits;

        dataArray[dataIndex] = data & ~(mask << bitIndex) | ((long)value & mask) << bitIndex;
    }

    /**
     * @reason Optimise method to use our magic value, which does not perform an add
     * @author Spottedleaf
     */
    @Overwrite
    @Override
    public int get(final int index) {
        // assume index in range
        final long magic = this.magic;
        final int bits = this.bits;
        final long mul = magic >>> 32;
        final int dataIndex = (int)(((long)index * mul) >>> magic);

        final long mask = (1L << bits) - 1; // avoid extra memory read
        final long data = this.data[dataIndex];

        final int bitIndex = (index - (dataIndex * this.valuesPerLong)) * bits;

        return (int)(data >> bitIndex & mask);
    }
}
