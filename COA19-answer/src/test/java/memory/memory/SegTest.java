package memory.memory;

import cpu.MMU;
import memory.Memory;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * 分段模式下读取数据，需要将逻辑地址转线性地址，线性地址等效于物理地址
 */
public class SegTest {

	static MMU mmu;

	static Memory memory;

	static MemTestHelper helper;

	@BeforeClass
	public static void init() {
		mmu = MMU.getMMU();
		Memory.PAGE = false;
		Memory.SEGMENT = true;
		memory = Memory.getMemory();
		helper = new MemTestHelper();
	}

	@Test
	public void test1() {
		String eip = "00000000000000000000000000000000";
		int len = 128;
		char[] data = helper.fillData((char)0b00001111, len);
		memory.alloc_seg_force(0, eip, len, false, eip);
		assertArrayEquals(data, mmu.read("000000000000000000000000000000000000000000000000", len));
	}

	/**
	 * Situation: 总空间不足使用LRU+碎片整理
	 * 8 4 -4 8 8
	 * + 5 MB
	 * 8 4 -4 8 -8    	Step1: LRU抛弃段
	 * 8 4 8 5 -7	 	Step2: 整理碎片+最先适应段填入
	 */
	@Test
	public void test2() {
		initTest234();
		char[] diskData = helper.fillData((char)0b00000011, 5 * 1024 * 1024);
		memory.alloc_seg_force(4, "00000000000000000000000000000000", 5 * 1024 * 1024, false, "00000001010000000000000000000000"); // 重新将数据加载到内存时，段表中的基地址会重新分配
		assertArrayEquals(diskData, mmu.read("000000000010000000000000000000000000000000000000", 5 * 1024 * 1024));
	}

	/**
	 * Situation: 总空间足够使用碎片整理
	 * 8 4 -4 8 8
	 * + 12 MB
	 * 8 4 -4 8 -8		Step1: 修改Disk使失效
	 * 8 4 8 9 -3		Step2: 整理碎片+最先适应段填入
	 */
	@Test
	public void test3() {
		initTest234();
		memory.invalid(3, -1);
		char[] diskData = helper.fillData((char)0b00000011, 9 * 1024 * 1024);
		memory.alloc_seg_force(4, "00000000000000000000000000000000", 9 * 1024 * 1024, false, "00000001010000000000000000000000");
		assertArrayEquals(diskData, mmu.read("000000000010000000000000000000000000000000000000", 9 * 1024 * 1024));
	}

	/**
	 * Situation: 最先适应
	 * 8 4 -4 8 8
	 * + 6 MB
	 * 8 4 -4 8 -8		Step1: 修改Disk使失效
	 * 8 4 -4 8 5 -3	Step2: 最先适应段填入
	 */
	@Test
	public void test4() {
		initTest234();
		memory.invalid(3, -1);
		char[] diskData = helper.fillData((char)0b00000011, 5 * 1024 * 1024);
		memory.alloc_seg_force(4, "00000000000000000000000000000000", 5 * 1024 * 1024, false, "00000001100000000000000000000000");
		assertArrayEquals(diskData, mmu.read("000000000010000000000000000000000000000000000000", 5 * 1024 * 1024));
	}

	/**
	 * 将内存初始化为8M  4M  空闲4M  8M  8M的状态
	 * 8 4 -4 8 8	   更新LRU时间戳信息为(1, 2, 3, 0)
	 */
	private void initTest234() {
		// 初始化
		char[] initData = helper.fillData((char)0b00001111, 32 * 1024 * 1024);
		memory.write("00000000000000000000000000000000", 32 * 1024 * 1024, initData);
		memory.alloc_seg_force(0, "00000000000000000000000000000000", 8 * 1024 * 1024, true, "00000000000000000000000000000000");
		memory.alloc_seg_force(1, "00000000100000000000000000000000", 4 * 1024 * 1024, true, "00000000100000000000000000000000");
		memory.alloc_seg_force(2, "00000001000000000000000000000000", 8 * 1024 * 1024, true, "00000001000000000000000000000000");
		memory.alloc_seg_force(3, "00000001100000000000000000000000", 8 * 1024 * 1024, true, "00000001100000000000000000000000");
		// 更新时间戳
		memory.read("00000001100000000000000000000000", 8 * 1024 * 1024);	// read seg 3
		memory.read("00000000000000000000000000000000", 8 * 1024 * 1024);	// read seg 0
		memory.read("00000000100000000000000000000000", 4 * 1024 * 1024);	// read seg 1
		memory.read("00000001000000000000000000000000", 8 * 1024 * 1024);	// read seg 2
	}

	@After
	public void after() {
		helper.clearAll();
	}

}
