Compilation

Before anything, run:
    chmod +x runme.sh
This allows the runme.sh to become executable

To compile all Java source files and generate class files in the 'compiled' directory, run:
./runme.sh -c

Running the Services
The services should be started in separate terminal windows in the following order:

Start User Service:
   ./runme.sh -u

Start Product Service:
   ./runme.sh -p

Start Order Service (Gateway):
   ./runme.sh -o

Note: All services read their port and IP configurations from the root 'config.json' file.

To use the system with a workload file:
./runme.sh -w [workload_file_path]

Project Structure
- /src: Java and Python source code.
- /compiled: Compiled .class files and executables.
- /docs: Javadocs and the writeup.pdf (AI disclosure and A2 strategy).
- /tests: Sample workload files and test cases.