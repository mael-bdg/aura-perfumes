package view;

import presenter.ConsultationViewBoundary;

public class ConsoleConsultationView
        implements ConsultationViewBoundary {

    @Override
    public void display(String message) {
        System.out.println(message);
    }
}

