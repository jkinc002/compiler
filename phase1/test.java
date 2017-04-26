class Main {
	public static void main(String[] a){
		System.out.println(new A().run());
	}
}

class A {
	public int run() {
		int x;
		C q;
		x = q.cow();
		return x;
	}
}

class B {
	public int cow() {
		int y;
		return y;
	}


}

class C extends B {}

