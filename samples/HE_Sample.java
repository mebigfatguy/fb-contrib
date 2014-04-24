import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

//Expected bug count: 12
//5 HE_EXECUTOR_NEVER_SHUTDOWN 
//4 HE_LOCAL_EXECUTOR_SERVICE
//3 HE_EXECUTOR_OVERWRITTEN_WITHOUT_SHUTDOWN
public class HE_Sample {


	public static void main(String[] args) {
		LocalExecutorProblem p = new LocalExecutorProblem();
		p.task();

		System.out.println("Should end");
	}
}

class SampleExecutable implements Runnable {
	@Override
	public void run() {
		System.out.println("Hello");
	}

	//Dummy method with throws to simulate something potentially throwing exception
	public static void methodThrows() throws Exception{
		if (Math.random()<.5)
			throw new Exception("There was a problem with the RNG");
	}

}

class SingleThreadExecutorProblem {
	//tag
	private ExecutorService executor;

	public SingleThreadExecutorProblem() {
		this.executor = Executors.newSingleThreadExecutor();
	}

	public void test() {
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}

}

class SingleThreadExecutorGood {
	//no tag
	private ExecutorService executor;

	public SingleThreadExecutorGood() {
		this.executor = Executors.newSingleThreadExecutor();
	}
	public void test() {
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}
	public void shutDown() {
		executor.shutdown();
	}
}
class SingleThreadExecutorGood1 {
	//no tag
	private ExecutorService executor;

	public SingleThreadExecutorGood1() {
		this.executor = Executors.newSingleThreadExecutor();
	}
	public void test() {
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}
	public void shutDown() {
		executor.shutdownNow();
	}
}
class SingleThreadExecutorGood2 {
	//no tag
	private ExecutorService executor;

	public SingleThreadExecutorGood2() {
		this.executor = Executors.newSingleThreadExecutor();
	}
	public void test() {
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}
	public void shutDown() {
		try {
			executor.awaitTermination(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		executor.shutdown();
	}
}

class SingleThreadExecutorTryProblem {
	//this won't get tagged as of version 2.2  If given more thought, this could be implemented
	private ExecutorService executor;

	public SingleThreadExecutorTryProblem() {
		this.executor = Executors.newSingleThreadExecutor();
	}
	public void test() {
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}
	public void shutDown() {
		try {
			executor.awaitTermination(30, TimeUnit.SECONDS);
			executor.shutdown();		//this doesn't count as shutdown, so it should be tagged.
			//probably with a different bug
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}
}

class FixedThreadPoolProblem {
	//tag
	private ExecutorService executor;

	public FixedThreadPoolProblem() {
		this.executor = Executors.newFixedThreadPool(3);
	}

	public void test() {
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}

}

class CachedThreadPoolMehProblem {
	//tag - this is bad practice, even though JVM will exit after 60 seconds
	private ExecutorService executor;

	public CachedThreadPoolMehProblem() {
		this.executor = Executors.newCachedThreadPool();
	}

	public void test() {
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}

}

class SingleThreadExecutorThreadFactoryMehProblem {
	//tag - this is bad practice, even though the threads will terminate
	private ExecutorService executor;

	public SingleThreadExecutorThreadFactoryMehProblem() {
		this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable arg0) {
				Thread t = new Thread(arg0);
				t.setDaemon(true);
				return t;
			}
		});
	}

	public void test() {
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}

}

class ScheduledThreadPoolProblem {
	//tag
	private ExecutorService executor;

	public ScheduledThreadPoolProblem() {
		this.executor = Executors.newScheduledThreadPool(1);
	}

	public void test() {
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}

}

class ReplacementExecutorProblem {
	private ExecutorService executor;

	public ReplacementExecutorProblem() {
		this.executor = Executors.newScheduledThreadPool(1);
		executor.execute(new SampleExecutable());
	}

	public void reset() {
		
		executor.execute(new SampleExecutable());
		//tag (the old executor won't get picked up for garbage collection)
		this.executor = Executors.newScheduledThreadPool(1);
		executor.execute(new SampleExecutable());
		try {
			executor.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void test() {
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}

	public void shutDown() {
		executor.shutdownNow();
	}

}

class ReplacementExecutorGood {
	private ExecutorService executor;

	public ReplacementExecutorGood() {
		this.executor = Executors.newScheduledThreadPool(1);
		executor.execute(new SampleExecutable());
	}

	public void reset() {
		//no tag
		this.executor.shutdown();

		this.executor = Executors.newScheduledThreadPool(1);
		executor.execute(new SampleExecutable());
		try {
			executor.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		executor.shutdown();
	}

}

class ReplacementExecutorBad2 {
	private ExecutorService executor;

	public ReplacementExecutorBad2() {
		this.executor = Executors.newScheduledThreadPool(1);
		executor.execute(new SampleExecutable());
	}

	public void reset() {
		//tag, because shutdown in other method isn't forced to be called
		this.executor = Executors.newScheduledThreadPool(1);

	}

	public void shutDown() {
		this.executor.shutdown();
	}

	public void task() {
		executor.execute(new SampleExecutable());
		try {
			executor.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}

class ReplacementExecutorGood2 {
	private ExecutorService executor;

	public ReplacementExecutorGood2() {
		this.executor = Executors.newScheduledThreadPool(1);
		executor.execute(new SampleExecutable());
	}

	public void reset() {
		System.out.println("Pretest");
		if (executor == null) {
			//no tag, the null check indicates some thought that another threadpool won't get left behind
			this.executor = Executors.newScheduledThreadPool(1);
		}
		//tag (this one is no long under the "good graces" of the null check
		this.executor = Executors.newCachedThreadPool();
	}

	public void shutDown() {
		this.executor.shutdown();
		//no tag
		this.executor = null;
	}

	public void task() {
		executor.execute(new SampleExecutable());
		try {
			executor.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}


class LocalExecutorProblem {

	public void task() {
		//tag
		ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}

}

class LocalExecutorProblem1 {

	public void task() {
		//tag
		ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
			
			@Override
			public Thread newThread(Runnable arg0) {
				return new Thread(arg0);
			}
		});
		executor.execute(new SampleExecutable());
		executor.execute(new SampleExecutable());
	}

}

class LocalExecutorProblem2 {

	public void task() {
		//tag (checking for mislabeled objects)
		Object executor = Executors.newCachedThreadPool(new ThreadFactory() {
			
			@Override
			public Thread newThread(Runnable arg0) {
				return new Thread(arg0);
			}
		});
		
		System.out.println(executor);
	}

}

class LocalExecutorProblem3 {

	public void task() {
		//tag
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
		
		System.out.println(executor);
	}

}



