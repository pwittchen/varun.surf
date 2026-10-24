package surf.varun.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WindClassTest {
    @Test
    fun cardClassesMatchWeb() {
        assertThat(WindClass.of(0.0)).isEqualTo(WindClass.WEAK)
        assertThat(WindClass.of(11.99)).isEqualTo(WindClass.WEAK)
        assertThat(WindClass.of(12.0)).isEqualTo(WindClass.MODERATE)
        assertThat(WindClass.of(17.99)).isEqualTo(WindClass.MODERATE)
        assertThat(WindClass.of(18.0)).isEqualTo(WindClass.STRONG)
        assertThat(WindClass.of(25.0)).isEqualTo(WindClass.STRONG)
        assertThat(WindClass.of(25.01)).isEqualTo(WindClass.EXTREME)
    }

    @Test
    fun mapSplitsTheWeakRange() {
        assertThat(WindClass.forMap(4.9)).isEqualTo(WindClass.CALM)
        assertThat(WindClass.forMap(5.0)).isEqualTo(WindClass.LIGHT)
        assertThat(WindClass.forMap(11.9)).isEqualTo(WindClass.LIGHT)
        assertThat(WindClass.forMap(12.0)).isEqualTo(WindClass.MODERATE)
        assertThat(WindClass.forMap(30.0)).isEqualTo(WindClass.EXTREME)
    }

    @Test
    fun averageClassUsesMeanOfWindAndGusts() {
        assertThat(WindClass.ofAverage(10.0, 14.0)).isEqualTo(WindClass.MODERATE)
        assertThat(WindClass.ofAverage(10.0, 13.0)).isEqualTo(WindClass.WEAK)
        assertThat(WindClass.MODERATE.isRideable).isTrue()
        assertThat(WindClass.LIGHT.isRideable).isFalse()
    }
}
