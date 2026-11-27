using Skua.Core.Interfaces;
using Skua.Core.Interfaces.ViewModels;
using Skua.Core.Models;

namespace SkuaCraft.PlayerHost;

public sealed class PlayerDialogService : IDialogService
{
    public bool? ShowDialog<TViewModel>(TViewModel viewModel) where TViewModel : class => false;

    public bool? ShowDialog<TViewModel>(TViewModel viewModel, string title) where TViewModel : class => false;

    public bool? ShowDialog<TViewModel>(TViewModel viewModel, Action<TViewModel> callback) where TViewModel : class => false;

    public void ShowDialog(IOptionContainer optionContainer, Action<IOptionContainerViewModel> callback)
    {
    }

    public void ShowMessageBox(string message, string caption)
    {
        Console.WriteLine($"[Skua dialog] {caption}: {message}");
    }

    public bool? ShowMessageBox(string message, string caption, bool yesAndNo)
    {
        Console.WriteLine($"[Skua dialog] {caption}: {message}");
        return false;
    }

    public Skua.Core.Models.DialogResult ShowMessageBox(string message, string caption, params string[] buttons)
    {
        Console.WriteLine($"[Skua dialog] {caption}: {message}");
        return Skua.Core.Models.DialogResult.Cancelled;
    }

    public IInputDialogViewModel CreateInputDialog(string title, string dialogHint)
    {
        return new PlayerInputDialogViewModel(title, dialogHint);
    }
}

public sealed class PlayerInputDialogViewModel : IInputDialogViewModel
{
    public PlayerInputDialogViewModel(string title, string dialogHint)
    {
        Title = title;
        DialogHint = dialogHint;
    }

    public string Title { get; }

    public bool NumberOnly => false;

    public string DialogTextInput { get; set; } = string.Empty;

    public string DialogHint { get; }

    public string TextBoxHint => string.Empty;
}
