/*  MicroJava Parser (HM 06-12-28)
    ================
*/
package MJ;

import MJ.CodeGen.Code;
import MJ.CodeGen.Operand;
import MJ.SymTab.Obj;
import MJ.SymTab.Struct;
import MJ.SymTab.Tab;
import java.util.*;


public class Parser {
	private static final int  // token codes
		none      = 0,
		ident     = 1,
		number    = 2,
		charCon   = 3,
		plus      = 4,
		minus     = 5,
		times     = 6,
		slash     = 7,
		rem       = 8,
		eql       = 9,
		neq       = 10,
		lss       = 11,
		leq       = 12,
		gtr       = 13,
		geq       = 14,
		assign    = 15,
		semicolon = 16,
		comma     = 17,
		period    = 18,
		lpar      = 19,
		rpar      = 20,
		lbrack    = 21,
		rbrack    = 22,
		lbrace    = 23,
		rbrace    = 24,
		class_    = 25,
		else_     = 26,
		final_    = 27,
		if_       = 28,
		new_      = 29,
		print_    = 30,
		program_  = 31,
		read_     = 32,
		return_   = 33,
		void_     = 34,
		while_    = 35,
		eof       = 36;
	private static final String[] name = { // token names for error messages
		"none", "identifier", "number", "char constant", "+", "-", "*", "/", "%",
		"==", "!=", "<", "<=", ">", ">=", "=", ";", ",", ".", "(", ")",
		"[", "]", "{", "}", "class", "else", "final", "if", "new", "print",
		"program", "read", "return", "void", "while", "eof"
		};

	private static Token t;			// current token (recently recognized)
	private static Token la;		// lookahead token
	private static int sym;			// always contains la.kind
	public  static int errors;  // error counter
	private static int errDist;	// no. of correctly recognized tokens since last error

	private static BitSet exprStart, statStart, statSeqFollow, declStart, declFollow, syncStart;

	//------------------- auxiliary methods ----------------------
	private static void scan() {
		t = la;
		la = Scanner.next();
		sym = la.kind;
		errDist++;
		/*
		System.out.print("line " + la.line + ", col " + la.col + ": " + name[sym]);
		if (sym == ident) System.out.print(" (" + la.string + ")");
		if (sym == number || sym == charCon) System.out.print(" (" + la.val + ")");
		System.out.println();*/
	}

	private static void check(int expected) {
		if (sym == expected) scan();
		else error(name[expected] + " expected");
	}

	public static void error(String msg) { // syntactic error at token la
		if (errDist >= 3) {
			System.out.println("-- line " + la.line + " col " + la.col + ": " + msg);
			errors++;
		}
		errDist = 0;
	}

	//-------------- parsing methods (in alphabetical order) -----------------

	// Program = "program" ident {ConstDecl | ClassDecl | VarDecl} '{' {MethodDecl} '}'.
	private static void Program() {
        check(program_);
        check(ident);
        Tab.openScope();
        while(declStart.get(sym)) {
            if(sym == final_) {
                ConstDecl();
            } else if(sym == ident) {
                VarDecl();
            } else if(sym == class_) {
                Classdecl();
            }
        }
        check(lbrace);
        while(sym == ident || sym == void_) {
            MethodDecl();
        }
        check(rbrace);
        Tab.closeScope();
	}
    //ConstDecl = "final" Type ident "=" (number | charConst) ";".
    private static void ConstDecl() {
        check(final_);
        Struct type = Type();
        check(ident);
        Obj con = Tab.insert(Obj.Con, t.string, type);
        check(assign);
        if(sym == number) {
            scan();
            if(con.type.kind == Struct.Int)
                con.val = t.val;
            else
                error("You can't assing a int in a char type variable");
        } else if(sym == charCon) {
            scan();
            if(con.type.kind == Struct.Char)
                con.val = t.val;
            else
                error("You can't assing a char in a int type variable");
        } else {
            error("invalid ConstDecl");
        }
        Tab.dumpScope(con);
        check(semicolon);
    }
    //VarDecl = Type ident {"," ident } ";".
    private static void VarDecl() {
        Struct type = Type();
        check(ident);
        Tab.insert(Obj.Var, t.string, type);
        while(sym == comma) {
            scan();
            check(ident);
            Tab.insert(Obj.Var, t.string, type);
        }
        check(semicolon);
    }
    //ClassDecl = "class" ident "{" {VarDecl} "}".
    private static void Classdecl() {
        check(class_);
        check(ident);
        Obj newClass = Tab.insert(Obj.Type, t.string, new Struct(Struct.Class));
        Tab.openScope();
        check(lbrace);
        while(sym == ident) {
            VarDecl();
        }
        newClass.type.fields = Tab.curScope.locals;
        newClass.type.nFields = Tab.curScope.nVars;
        check(rbrace);
        Tab.dumpScope(newClass);
        Tab.closeScope();

    }
    //MethodDecl = (Type | "void") ident "(" [FormPars] ")" {VarDecl} Block.
    private static void MethodDecl() {
        Struct type = new Struct(Struct.None);
        if(sym == void_) {
            scan();
        } else if(sym == ident) {
            type = Type();
        } else {
            error("Void or identifier expected");
        }
        check(ident);
        Obj method = Tab.insert(Obj.Meth, t.string, type);
        if (method.name.equals("main")) {
            if (method.type.kind != Tab.noType.kind) {
                error("method main must be void");
            }
            if (method.nPars != 0) {
                error("main must not have parameters");
            }
        }
        Tab.curMethod = method;
        Tab.openScope();
        check(lpar);
        if(sym == ident) {
            method.nPars = FormPars();
        }
        check(rpar);
        if(method.name.equals("main"))
            Code.mainPc = Code.pc;
        method.nPars = Tab.curScope.nVars;
        while(sym == ident) {
            VarDecl();
        }
        method.locals = Tab.curScope.locals;
        method.adr = Code.pc;
        Code.put(Code.enter);
        Code.put(method.nPars);
        Code.put(Tab.curScope.nVars);
        Block();
        if (method.type.kind == Tab.noType.kind) {
            Code.put(Code.exit);
            Code.put(Code.return_);
        } else { // end of function reached without a return statement
            Code.put(Code.trap);
            Code.put(1);
        }
        Tab.dumpScope(method);
        Tab.closeScope();
    }
    //FormPars = Type ident {"," Type ident}.
    private static int FormPars() {
        int n = 0;
        if(sym == ident) {
            FormPar();
            n++;
        }
        while(sym == comma) {
            scan();
            FormPar();
            n++;
        }
        return n;
    }

    private static void FormPar() {
        Struct type = Type();
        check(ident);
        Tab.dumpScope(Tab.insert(Obj.Var, t.string, type));
    }

    //Type = ident ["[" "]"].
    private static Struct Type() {
        check(ident);
        Obj x = Tab.find(t.string);
        Struct type = x.type;
        if(x.kind != Obj.Type) {
            error("type expected");
        }
        if(sym == lbrack) {
            scan();
            check(rbrack);
            type = new Struct(Struct.Arr);
            type.elemType = x.type;
        }
        return type;
    }
    //Block = "{" {Statement} "}".
    private static void Block() {
        check(lbrace);
        while(sym != rbrace && sym != eof) {
            Statement();
        }
        check(rbrace);
    }
    /*   Designator ("=" Expr | ActPars) ";"
        | "if" "(" Condition ")" Statement ["else" Statement]
        | "while" "(" Condition ")" Statement
        | "return" [Expr] ";"
        | "read" "(" Designator ")" ";"
        | "print" "(" Expr ["," number] ")" ";"
        | Block
        | ";". */
    private static void Statement() {
        if (!statStart.get(sym)) {
            error("invalid start of statement");
            while (!syncStart.get(sym)){scan();}
            errDist = 0;
        }
        if(sym == ident) {
            Operand x = Designator();
            if(sym == assign) {
                scan();
                Operand y = Expr();
                if (y.type.assignableTo(x.type)) {
                    Code.assign(x, y); // x: Local | Static | Fld | Elem
                    // assign must load y
                } else {
                    error("incompatible types in assignment");
                }
            } else if(sym == lpar) {
                ActPars(x);
                Code.put(Code.call);
                Code.put2(x.adr);
                if (x.type.kind != Tab.noType.kind)
                    Code.put(Code.pop);
            } else {
                error("invalid assignment or call, = or '(' expected");
            }
            check(semicolon);
        } else if(sym == if_) {
            scan();
            check(lpar);
            int op = Condition();
            check(rpar);
            Code.putFalseJump(op,0);
            int adr = Code.pc-2; //storing the adress to fixup later
            Statement();
            if(sym == else_) {
                scan();
                Code.putJump(0);
                int adr2 = Code.pc-2;
                Code.fixup(adr);
                Statement();
                Code.fixup(adr2);
            } else {
                Code.fixup(adr);
            }
        } else if(sym == while_) {
            scan();
            int top = Code.pc;
            check(lpar);
            int op = Condition();
            Code.putFalseJump(op, 0);
            int adr = Code.pc-2;
            check(rpar);
            Statement();
            Code.putJump(top);
            Code.fixup(adr);
        } else if(sym == return_) {
            scan();
            Operand x;
            if(exprStart.get(sym)) {
                x = Expr();
                Code.load(x);
                if (Tab.curMethod.type.kind == Tab.noType.kind){
                    error("void method must not return a value");
                } else if (!x.type.assignableTo(Tab.curMethod.type)){
                    error("type of return value must match method type");
                }
            } else {
                if (Tab.curMethod.type != Tab.noType) {
                    error("return value expected");
                }
            }
            Code.put(Code.exit);
            Code.put(Code.return_);
            check(semicolon);
        } else if(sym == read_) {
            scan();
            check(lpar);
            Operand x = Designator();
            if(x.type.kind == Tab.charType.kind) {
                Code.put(Code.bread);
                Operand readVal = new Operand(Operand.Stack, 0, Tab.intType);
                Code.assign(x, readVal);
            } else if (x.type.kind == Tab.intType.kind){
                Code.put(Code.read);
                Operand readVal = new Operand(Operand.Stack, 0, Tab.intType);
                Code.load(readVal);
                Code.assign(x, readVal);
            } else {
                error("read only accept integer and char type value");
            }
            check(rpar);
            check(semicolon);
        } else if(sym == print_) {
            scan();
            check(lpar);
            Operand x = Expr();
            if(x.type.kind != Tab.charType.kind && x.type.kind != Tab.intType.kind) {
                error("print only accept integer and char type value");
            }
            Code.load(x);
            if(sym == comma) {
                scan();
                check(number);
                Code.load(new Operand(t.val));
            } else {
                Code.put(Code.const0);
            }
            if(x.type.kind == Tab.charType.kind) {
                Code.put(Code.bprint);
            } else {
                Code.put(Code.print);
            }
            check(rpar);
            check(semicolon);
        } else if(sym == lbrace) {
            Block();
        } else if(sym == semicolon) {
            scan();
        }

    }
    //ActPars = "(" [ Expr {"," Expr} ] ")".
    private static void ActPars(Operand x) {
        check(lpar);
        if (x.kind != Operand.Meth) {
            error("not a method"); x.obj = Tab.noObj;
        }
        int aPars = 0;
        int fPars = x.obj.nPars;
        Obj fp = x.obj.locals;
        if(exprStart.get(sym)) {
            Operand ap = Expr();
            Code.load(ap);
            aPars++;
            if (fp != null) {
                if (!ap.type.assignableTo(fp.type)) error("parameter type mismatch");
                fp = fp.next;
            }
            while (sym == comma) {
                scan();
                ap = Expr();
                Code.load(ap);
                aPars++;
                if (fp != null) {
                    if (!ap.type.assignableTo(fp.type)) {
                        error("parameter type mismatch");
                    }
                    fp = fp.next;
                }
            }
        }
        if (aPars > fPars) {
            error("too many actual parameters");
        } else if (aPars < fPars) {
            error("too few actual parameters");
        }
        check(rpar);
    }
    //Condition = Expr Relop Expr.
    private static int Condition() {
        Operand x = Expr();
        Code.load(x);
        int op = Relop();
        Operand y = Expr();
        Code.load(y);
        if (!x.type.compatibleWith(y.type))
            error("type mismatch");
        if (x.type.isRefType() && op != Code.eq && op != Code.ne)
            error("invalid compare");
        return op;
    }
    //Relop = "==" | "!=" | ">" | ">=" | "<" | "<=".
    private static int Relop() { //TODO: refactor
        if(sym == eql) {
            scan();
            return Code.eq;
        } else if(sym == neq) {
            scan();
            return Code.ne;
        } else if(sym == gtr) {
            scan();
            return Code.gt;
        } else if(sym == geq) {
            scan();
            return Code.ge;
        } else if(sym == lss) {
            scan();
            return Code.lt;
        } else if(sym == leq) {
            scan();
            return Code.le;
        } else {
            error("Relation operator expected");
            return Code.eq; // avoid critic error
        }
    }
    //Expr = ["-"] Term {Addop Term}.
    private static Operand Expr() {
        Operand x;
        if(sym == minus) {
            scan();
            x = Term();
            if (x.type.kind != Tab.intType.kind) {
                error("operand must be of type int");
            }
            if (x.kind == Operand.Con) {
                x.val = -x.val;
            } else {
                Code.load(x); Code.put(Code.neg);
            }
        } else {
            x = Term();
        }
        while(sym == minus || sym == plus) {
            int op = Addop();
            Code.load(x);
            Operand y = Term();
            Code.load(y);
            if (x.type.kind != Tab.intType.kind || y.type.kind != Tab.intType.kind)
                error("operands must be of type int");
            Code.put(op);
        }
        return x;
    }
    //Term = Factor {Mulop Factor}.
    private static Operand Term() {
        Operand x = Factor();
        while(sym == times || sym == slash || sym == rem) {
            int op = Mulop();
            Code.load(x);
            Operand y = Factor();
            Code.load(y);
            if (x.type.kind != Tab.intType.kind || y.type.kind != Tab.intType.kind) {
                error("operands must be of type int");
            }
            Code.put(op);
        }
        return x;
    }
    /*Factor = Designator [ActPars]
     | number
     | charConst
     | "new" ident ["[" Expr "]"]
     | "(" Expr ")". */
    private static Operand Factor() {
        Operand x;
        if(sym == ident) {
            x = Designator();
            if(sym == lpar) {
                ActPars(x);
                if (x.type.kind == Tab.noType.kind) {
                    error("procedure called as a function");
                }
                if (x.obj.kind == Tab.ordObj.kind || x.obj.kind == Tab.chrObj.kind) {
                    ;// nothing
                } else if (x.obj.kind == Tab.lenObj.kind) {
                    Code.put(Code.arraylength);
                } else {
                    Code.put(Code.call);
                    Code.put2(x.adr);
                }
                x.kind = Operand.Stack;
            }
        } else if(sym == number) {
            scan();
            x = new Operand(t.val);
        } else if(sym == charCon) {
            scan();
            x = new Operand(t.val);
            x.type = Tab.charType;
        } else if(sym == new_) {
            scan();
            check(ident);
            Obj obj = Tab.find(t.string);
            Struct type = obj.type;//type de new Table = prog ?? should be Type
            if(sym == lbrack) {
                scan();
                if (obj.kind != Obj.Type)
                    error("type expected");
                x = Expr();
                if (x.type != Tab.intType)
                    error("array size must be of type int");
                Code.load(x);
                Code.put(Code.newarray);
                if (type.kind == Tab.charType.kind)
                    Code.put(0);
                else
                    Code.put(1);
                type = new Struct(Struct.Arr, type);
                check(rbrack);
            } else {
                if (obj.kind != Obj.Type || type.kind != Struct.Class)
                    error("class type expected");
                Code.put(Code.new_); Code.put2(type.nFields);
            }
            x = new Operand(Operand.Stack, 0, type);
        } else if(sym == lpar) {
            scan();
            x = Expr();
            check(rpar);
        } else {
            error("only ident, number, charCon, new keyword, '(' are expected.");
            x = new Operand(-1); //TODO
        }
        return x;
    }
    //Designator = ident {"." ident | "[" Expr "]"}.
    private static Operand Designator() {
        check(ident);
        String name = t.string;
        Obj obj = Tab.find(name);
        Operand x = new Operand(obj);
        while(sym == period || sym == lbrack) {
            if(sym == period) {
                scan();
                check(ident);
                if(x.type.kind == Struct.Class) {
                    Code.load(x);
                    Obj fld = Tab.findField(t.string, x.type);
                    x.kind = Operand.Fld;
                    x.adr = fld.adr;
                    x.type = fld.type;
                } else {
                    error(name + " is not an object");
                }
            } else if(sym == lbrack) {
                scan();
                if(x.type.kind == Struct.Arr) {
                    Code.load(x);
                    Operand y = Expr();
                    if (y.type.kind != Struct.Int) error("index must be of type int");
                    Code.load(y);
                    x.kind = Operand.Elem;
                    x.type = x.type.elemType;
                } else
                    error(name + " is not an array");
                check(rbrack);
            }
        }
        return x;
    }
    //Addop = "+" | "-".
    private static int Addop() {
        if(sym == plus || sym == minus) {
            if(sym == plus) {
                scan();
                return Code.add;
            } else {
                scan();
                return Code.sub;
            }
        } else {
            error("plus or minus expected");
            return Code.add; //to avoid critic error
        }
    }
    //Mulop = "*" | "/" | "%".
    private static int Mulop() {
        if(sym == times || sym == slash || sym == rem) {
            scan();
            switch (sym) {
                case times:
                    return Code.mul;
                case slash:
                    return Code.div;
                default:
                    return Code.rem;
            }
        } else {
            error("times, slash or rem expected");
            return Code.mul; //avoid critic error
        }
    }

	public static void parse() {
		// initialize symbol sets
		BitSet s;
		s = new BitSet(64); exprStart = s;
		s.set(ident); s.set(number); s.set(charCon); s.set(new_); s.set(lpar); s.set(minus);

        s = new BitSet(64); statStart = s;
        s.set(ident); s.set(if_); s.set(while_); s.set(read_);
        s.set(return_); s.set(print_); s.set(lbrace); s.set(semicolon);

        s = new BitSet(64); syncStart = s;
        s.set(if_); s.set(while_); s.set(read_); s.set(rbrace); s.set(eof);
        s.set(return_); s.set(print_); s.set(lbrace); s.set(semicolon);

		s = new BitSet(64); statSeqFollow = s;
		s.set(rbrace); s.set(eof);

		s = new BitSet(64); declStart = s;
		s.set(final_); s.set(ident); s.set(class_);

		s = new BitSet(64); declFollow = s;
		s.set(lbrace); s.set(void_); s.set(eof);

		// start parsing
		errors = 0; errDist = 3;
		scan();
        Tab.init();
        Code.init();
		Program();
        Code.write(System.out);
		if (sym != eof) error("end of file found before end of program");
	}

}








