package org.example;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.StatusTextEvent;
import org.eclipse.swt.browser.StatusTextListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.browser.Browser;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) {
        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setText("SWT Hidden");
        shell.setSize(1600, 1200);
        shell.setLayout(new FillLayout());

        Shell shell1 = new Shell(display);
        shell1.setText("SWT WebView2 Example (1)");
        shell1.setLayout(new FillLayout());

        Shell shell2 = new Shell(display);
        shell2.setText("SWT WebView2 Example (2)");
        shell2.setLayout(new FillLayout());

        // Create a Browser widget that uses WebView2 on Windows
        Browser browser = new Browser(shell, SWT.BORDER | SWT.EDGE);
        browser.setUrl("https://www.google.com");

        scheduler.schedule(() -> {
            System.out.println("going to 1");
            display.asyncExec(() -> {
                browser.setParent(shell1);
                shell1.open();
                shell1.setSize(1600, 1200);
            });
        }, 1, TimeUnit.SECONDS);

        scheduler.schedule(() -> {
            System.out.println("going to 2");
            display.asyncExec(() -> {
                browser.setParent(shell2);
                shell2.open();
                shell2.setSize(1600, 1200);
            });
        }, 6, TimeUnit.SECONDS);

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }
        display.dispose();

        scheduler.shutdown();
    }
}