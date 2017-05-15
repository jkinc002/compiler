class Main {
	public static void main(String[] a){
		System.out.println(new A().run(true && false));
	}
}

class A {
	public int run(Boolean b) {
		int x;
		return x;
	}
}

class C extends A {}

